package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.CommentStatus
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import com.nexters.gamss.llm.generation.CommentGenerationOutput
import com.nexters.gamss.llm.generation.CommentGenerator
import com.nexters.gamss.llm.generation.LlmRetryExecutor
import com.nexters.gamss.llm.generation.LlmRetryPolicy
import com.nexters.gamss.llm.generation.ReplyGenerationOutput
import com.nexters.gamss.llm.generation.TokenUsageAccumulator
import com.nexters.gamss.llm.parsing.CommentFeedValidator
import com.nexters.gamss.llm.prompt.CommentPromptContext
import com.nexters.gamss.llm.prompt.PastSummaries
import com.nexters.gamss.llm.prompt.PromptCharacterId
import com.nexters.gamss.llm.selection.CharacterSelector
import com.nexters.gamss.llm.selection.EongttungTopicSelector
import com.nexters.gamss.llm.selection.PastSummaryPolicy
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.service.GenerationLogRecorder
import com.nexters.gamss.tokenlimit.service.DailyTokenLimitService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 선점(CAS) -> LLM 호출+검증(트랜잭션 밖, 최대 [LlmRetryPolicy.MAX_ATTEMPTS]회) -> 저장을 오케스트레이션한다.
 * 이 클래스 자체는 @Transactional이 아니다 — 세 단계가 각자 다른 트랜잭션 경계(또는 트랜잭션 밖)에
 * 있어야 하기 때문이다(락/트랜잭션 안에 LLM 호출을 넣지 않는다).
 */
@Service
class CommentGenerationService(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val characterSelector: CharacterSelector,
    private val eongttungTopicSelector: EongttungTopicSelector,
    private val commentGenerator: CommentGenerator,
    private val commentFeedValidator: CommentFeedValidator,
    private val commentPersistenceService: CommentPersistenceService,
    private val generationLogRecorder: GenerationLogRecorder,
    private val dailyTokenLimitService: DailyTokenLimitService,
    private val llmRetryExecutor: LlmRetryExecutor = LlmRetryExecutor(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 저장 직후 같은 요청 안에서 곧바로 생성까지 처리하는 진입점이다. [message]는 방금 저장돼
     * memberId 소유·채팅방 활성 상태가 이미 보장된 값이라(같은 요청의 saveUserMessage가 검증함)
     * [getOwnedRootMessage]의 소유권·삭제 여부 재확인을 생략한다. 답장 여부는 message 스스로 아는
     * 정보([Message.repliesToMessageId])라, 어떤 생성 흐름을 탈지는 호출자(컨트롤러)가 아니라
     * 여기서 정한다.
     */
    fun generateFor(
        memberId: Long,
        message: Message,
        currentConversationSummary: String?,
    ): GenerationResult {
        limitExceededOrNull(memberId)?.let { return it }
        if (message.repliesToMessageId == null) {
            return generateCommentsInternal(memberId, message, message.id, currentConversationSummary)
        }
        return generateReplyInternal(memberId, message, message.id)
    }

    /**
     * 실패 후 수동 재시도용(멱등). [messageId]가 본인 소유의 일기(사용자) 메시지인지 새로 검증한다.
     * [currentConversationSummary]는 프론트가 재시도 요청에도 함께 보내면 재생성에 반영되고,
     * 생략하면(null) 맥락 없이 재생성한다.
     */
    fun generateComments(
        memberId: Long,
        messageId: Long,
        currentConversationSummary: String?,
    ): GenerationResult {
        val rootMessage = getOwnedRootMessage(memberId, messageId)
        limitExceededOrNull(memberId)?.let { return it }
        return generateCommentsInternal(memberId, rootMessage, messageId, currentConversationSummary)
    }

    /** 실패 후 수동 재시도용(멱등). [messageId]가 본인 소유의 답글(사용자) 메시지인지 새로 검증한다. */
    fun generateReplyComment(
        memberId: Long,
        messageId: Long,
    ): GenerationResult {
        val userReplyMessage = getOwnedRootMessage(memberId, messageId)
        limitExceededOrNull(memberId)?.let { return it }
        return generateReplyInternal(memberId, userReplyMessage, messageId)
    }

    /**
     * 생성 진입점 공통 상한 가드. 초과면 [CommentGenerationOutcome.LIMIT_EXCEEDED] 결과를, 아니면 null을 돌려준다.
     * 저장은 이미 끝난 상태라(저장 O, 생성만 차단) 여기선 생성을 건너뛰고 상태만 알린다 —
     * 새 생성 경로가 늘어도 이 한 곳으로 가드를 강제해 우회를 막는다.
     */
    private fun limitExceededOrNull(memberId: Long): GenerationResult? {
        if (dailyTokenLimitService.isWithinLimit(memberId)) {
            return null
        }
        return GenerationResult(CommentGenerationOutcome.LIMIT_EXCEEDED)
    }

    private fun generateCommentsInternal(
        memberId: Long,
        rootMessage: Message,
        messageId: Long,
        currentConversationSummary: String?,
    ): GenerationResult {
        val claimed =
            messageRepository.updateCommentStatus(
                messageId,
                CommentStatus.PENDING,
                listOf(CommentStatus.NONE, CommentStatus.FAILED),
                Instant.now(),
            )
        if (claimed == 0) {
            return currentStatusResult(messageId)
        }

        return try {
            // 대화방이 새로 생성될 때 지정한 제외 캐릭터 목록을 읽어오기 위한 조회다(소유권은 이미 검증된
            // 상태라 재검증 목적이 아니다 — generateFor는 저장 시점에, generateComments는 getOwnedRootMessage에서 확인함).
            val conversation =
                conversationRepository
                    .findById(rootMessage.conversationId)
                    .orElseThrow { BusinessException(ErrorCode.CONVERSATION_NOT_FOUND) }
            val pastSummaries =
                conversationRepository.findRandomPastSummaries(
                    memberId,
                    rootMessage.conversationId,
                    PastSummaryPolicy.POOL_SIZE,
                    PastSummaryPolicy.PICK_COUNT,
                )
            val output =
                generateWithRetry(
                    memberId,
                    rootMessage.conversationId,
                    rootMessage.content,
                    currentConversationSummary,
                    pastSummaries,
                    conversation.excludedEmotionTypes.toSet(),
                )
            val saved = commentPersistenceService.saveFeed(rootMessage.conversationId, messageId, output.feed)
            GenerationResult(CommentGenerationOutcome.DONE, saved, output.usedTokens)
        } catch (e: Exception) {
            logGenerationFailure("댓글 생성", messageId, e)

            messageRepository.updateCommentStatus(
                messageId,
                CommentStatus.FAILED,
                listOf(CommentStatus.PENDING),
                Instant.now(),
            )

            // 예상된 비즈니스 예외는 FAILED 전이 후에도 원래 의미(4xx 등)를 유지하도록 다시 던진다.
            if (e is BusinessException) throw e
            GenerationResult(CommentGenerationOutcome.FAILED)
        }
    }

    /**
     * 답글은 새로 캐릭터를 뽑지 않고 [characterMessage]가 이미 가진 emotionType을 그대로 재사용한다 —
     * 그 메시지 자체가 애초에 [CharacterSelector]로 제외 캐릭터를 걸러낸 뒤 뽑힌 결과라, 여기서 다시
     * 제외 목록을 확인할 필요가 없다(제외된 캐릭터가 답글로 되살아날 여지 자체가 없음).
     */
    private fun generateReplyInternal(
        memberId: Long,
        userReplyMessage: Message,
        messageId: Long,
    ): GenerationResult {
        val characterMessage =
            messageRepository
                .findById(
                    userReplyMessage.repliesToMessageId ?: throw BusinessException(ErrorCode.INVALID_COMMENT_TARGET),
                ).orElseThrow { BusinessException(ErrorCode.MESSAGE_NOT_FOUND) }
                .also { ensureSameConversation(it, userReplyMessage.conversationId) }
        if (characterMessage.senderType != SenderType.CHARACTER) {
            throw BusinessException(ErrorCode.INVALID_COMMENT_TARGET)
        }
        val diaryMessage =
            messageRepository
                .findById(characterMessage.rootMessageId ?: throw BusinessException(ErrorCode.INVALID_COMMENT_TARGET))
                .orElseThrow { BusinessException(ErrorCode.MESSAGE_NOT_FOUND) }
                .also { ensureSameConversation(it, userReplyMessage.conversationId) }

        val claimed =
            messageRepository.updateCommentStatus(
                messageId,
                CommentStatus.PENDING,
                listOf(CommentStatus.NONE, CommentStatus.FAILED),
                Instant.now(),
            )
        if (claimed == 0) {
            return currentReplyStatusResult(messageId)
        }

        return try {
            val output =
                generateReplyWithRetry(
                    memberId,
                    userReplyMessage.conversationId,
                    diaryMessage.content,
                    characterMessage,
                    userReplyMessage.content,
                )
            val saved =
                commentPersistenceService.saveReply(
                    conversationId = userReplyMessage.conversationId,
                    rootMessageId = diaryMessage.id,
                    repliesToMessageId = messageId,
                    characterId = characterMessage.emotionType!!,
                    text = output.text,
                )
            GenerationResult(CommentGenerationOutcome.DONE, listOf(saved), output.usedTokens)
        } catch (e: Exception) {
            logGenerationFailure("답글 생성", messageId, e)

            messageRepository.updateCommentStatus(
                messageId,
                CommentStatus.FAILED,
                listOf(CommentStatus.PENDING),
                Instant.now(),
            )

            // 예상된 비즈니스 예외는 FAILED 전이 후에도 원래 의미(4xx 등)를 유지하도록 다시 던진다.
            if (e is BusinessException) throw e
            GenerationResult(CommentGenerationOutcome.FAILED)
        }
    }

    /** LLM 호출 + 의미 검증을 하나의 단위로 묶어 최대 [LlmRetryPolicy.MAX_ATTEMPTS]회 시도한다. */
    private fun generateReplyWithRetry(
        memberId: Long,
        conversationId: Long,
        diaryContent: String,
        characterMessage: Message,
        userReply: String,
    ): ReplyGenerationOutput {
        val startedAt = System.currentTimeMillis()
        val tokens = TokenUsageAccumulator()
        val recordFailure: (Int, Exception) -> Unit = { attempt, e ->
            recordGeneration(GenerationType.REPLY, false, attempt, startedAt, memberId, conversationId, tokens, e)
        }

        return llmRetryExecutor.execute(
            retryOn = CommentGenerationFailedException::class,
            onAttemptFailure = { attempt, e ->
                tokens.addFailed(e)
                log.warn("답글 생성 {}차 시도 실패: {}", attempt, e.message)
            },
            onNonRetryable = recordFailure,
            onExhausted = recordFailure,
        ) { attempt ->
            val output =
                commentGenerator.generateReply(
                    diaryContent,
                    PromptCharacterId.of(characterMessage.emotionType!!).promptId,
                    characterMessage.content,
                    userReply,
                )
            try {
                commentFeedValidator.validateReply(output.text)
            } catch (e: CommentGenerationFailedException) {
                // 검증은 통과 못 했어도 호출은 됐으니, 실패 로그에 실제 과금 토큰이 남도록 실어 던진다.
                throw CommentGenerationFailedException(
                    e.message ?: "답글 검증 실패",
                    e.cause,
                    output.usedTokens,
                    output.cachedTokens,
                    output.inputTokens,
                    output.outputTokens,
                )
            }
            tokens.add(output)
            recordGeneration(GenerationType.REPLY, true, attempt, startedAt, memberId, conversationId, tokens)
            output
        }
    }

    /** 생성 로그의 실패 원인 요약. 근본 원인(예외 cause)의 클래스명을 우선 쓰고, 없으면 예외 자체의 클래스명을 쓴다. */
    private fun failureReasonOf(error: Throwable?): String? {
        if (error == null) {
            return null
        }
        return (error.cause ?: error).javaClass.simpleName
    }

    /** LLM 호출 + 의미 검증을 하나의 단위로 묶어 최대 [LlmRetryPolicy.MAX_ATTEMPTS]회 시도한다. */
    private fun generateWithRetry(
        memberId: Long,
        conversationId: Long,
        diaryContent: String,
        currentConversationSummary: String?,
        pastSummaries: List<String>,
        excludedCharacters: Set<EmotionType>,
    ): CommentGenerationOutput {
        val selection = characterSelector.select(excludedCharacters)
        val characters = selection.characters
        val tikitakaCount = selection.tikitakaCount
        val eongttungTopic = if (EmotionType.QUIRKY in characters) eongttungTopicSelector.select() else null

        val context =
            CommentPromptContext(
                currentConversationSummary = currentConversationSummary,
                pastSummaries = PastSummaries.of(pastSummaries),
                diaryContent = diaryContent,
                characters = characters,
                tikitakaCount = tikitakaCount,
                eongttungTopic = eongttungTopic,
            )
        val startedAt = System.currentTimeMillis()
        val tokens = TokenUsageAccumulator()
        // 재시도 대상이 아닌 예외로 중단할 때와 시도를 모두 소진했을 때는 남길 것이 같다 —
        // 그때까지 누적된 토큰과 마지막 실패 원인.
        val recordFailure: (Int, Exception) -> Unit = { attempt, e ->
            recordGeneration(GenerationType.COMMENT, false, attempt, startedAt, memberId, conversationId, tokens, e)
        }

        return llmRetryExecutor.execute(
            retryOn = CommentGenerationFailedException::class,
            onAttemptFailure = { attempt, e ->
                tokens.addFailed(e)
                log.warn("댓글 생성 {}차 시도 실패: {}", attempt, e.message)
            },
            onNonRetryable = recordFailure,
            onExhausted = recordFailure,
        ) { attempt ->
            val output = commentGenerator.generateComment(context)
            try {
                commentFeedValidator.validate(output.feed, characters, tikitakaCount)
            } catch (e: CommentGenerationFailedException) {
                // 검증은 통과 못 했어도 호출은 됐으니, 실패 로그에 실제 과금 토큰이 남도록 실어 던진다.
                throw CommentGenerationFailedException(
                    e.message ?: "댓글 검증 실패",
                    e.cause,
                    output.usedTokens,
                    output.cachedTokens,
                    output.inputTokens,
                    output.outputTokens,
                )
            }
            tokens.add(output)
            recordGeneration(GenerationType.COMMENT, true, attempt, startedAt, memberId, conversationId, tokens)
            output
        }
    }

    /**
     * 생성 로그 한 줄. 성공·실패 모두 [tokens]에 **그때까지 누적된 합계**를 싣는다 — 검증에 실패한
     * 시도도 호출은 됐으니 과금되기 때문에, 마지막 한 시도만 기록하면 비용이 과소 집계된다.
     */
    private fun recordGeneration(
        type: GenerationType,
        success: Boolean,
        attempt: Int,
        startedAt: Long,
        memberId: Long,
        conversationId: Long,
        tokens: TokenUsageAccumulator,
        error: Throwable? = null,
    ) {
        generationLogRecorder.record(
            type = type,
            success = success,
            attemptCount = attempt,
            latencyMs = System.currentTimeMillis() - startedAt,
            memberId = memberId,
            conversationId = conversationId,
            usedTokens = tokens.used,
            cachedTokens = tokens.cached,
            inputTokens = tokens.input,
            outputTokens = tokens.output,
            failureReason = failureReasonOf(error),
        )
    }

    private fun currentStatusResult(messageId: Long): GenerationResult {
        val message =
            messageRepository
                .findById(messageId)
                .orElseThrow { BusinessException(ErrorCode.MESSAGE_NOT_FOUND) }
        return when (message.commentStatus) {
            CommentStatus.DONE -> {
                GenerationResult(
                    CommentGenerationOutcome.DONE,
                    MessageThreadOrder.reorderTikitakaAfterTarget(messageRepository.findAllByRootMessageIdOrderByIdAsc(messageId)),
                )
            }

            else -> {
                GenerationResult(CommentGenerationOutcome.GENERATING)
            }
        }
    }

    private fun currentReplyStatusResult(messageId: Long): GenerationResult {
        val message =
            messageRepository
                .findById(messageId)
                .orElseThrow { BusinessException(ErrorCode.MESSAGE_NOT_FOUND) }
        if (message.commentStatus != CommentStatus.DONE) {
            return GenerationResult(CommentGenerationOutcome.GENERATING)
        }
        val reply =
            messageRepository.findByRepliesToMessageId(messageId)
                ?: run {
                    log.error("commentStatus는 DONE인데 답글 메시지를 찾을 수 없습니다. messageId={}", messageId)
                    return GenerationResult(CommentGenerationOutcome.FAILED)
                }
        return GenerationResult(CommentGenerationOutcome.DONE, listOf(reply))
    }

    // 생성 실패 로그: 예상된 실패(재시도 소진)는 warn, 예기치 않은 오류는 error로 남긴다.
    private fun logGenerationFailure(
        action: String,
        messageId: Long,
        e: Exception,
    ) {
        if (e is CommentGenerationFailedException) {
            log.warn("{} 최종 실패 messageId={}", action, messageId, e)
            return
        }
        log.error("{} 중 예기치 않은 오류 발생 messageId={}", action, messageId, e)
    }

    private fun getOwnedRootMessage(
        memberId: Long,
        messageId: Long,
    ): Message {
        val message =
            messageRepository
                .findById(messageId)
                .orElseThrow { BusinessException(ErrorCode.MESSAGE_NOT_FOUND) }
        if (message.senderType != SenderType.USER) {
            throw BusinessException(ErrorCode.INVALID_COMMENT_TARGET)
        }
        val conversation =
            conversationRepository
                .findById(message.conversationId)
                .orElseThrow { BusinessException(ErrorCode.CONVERSATION_NOT_FOUND) }
        if (!conversation.isOwnedBy(memberId)) {
            throw BusinessException(ErrorCode.CONVERSATION_ACCESS_DENIED)
        }
        conversation.ensureNotDeleted()
        return message
    }

    private fun ensureSameConversation(
        message: Message,
        conversationId: Long,
    ) {
        if (message.conversationId != conversationId) {
            throw BusinessException(ErrorCode.INVALID_COMMENT_TARGET)
        }
    }
}
