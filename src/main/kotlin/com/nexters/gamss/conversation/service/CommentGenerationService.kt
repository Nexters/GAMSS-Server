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
import com.nexters.gamss.llm.generation.LlmRetryPolicy
import com.nexters.gamss.llm.generation.ReplyGenerationOutput
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
 * 선점(CAS) -> LLM 호출+검증(트랜잭션 밖, 최대 2회) -> 저장을 오케스트레이션한다.
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
            val pastSummaries =
                conversationRepository.findRandomPastSummaries(
                    memberId,
                    rootMessage.conversationId,
                    PastSummaryPolicy.POOL_SIZE,
                    PastSummaryPolicy.PICK_COUNT,
                )
            val output =
                generateWithRetry(memberId, rootMessage.conversationId, rootMessage.content, currentConversationSummary, pastSummaries)
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
        var lastError: CommentGenerationFailedException? = null
        repeat(LlmRetryPolicy.MAX_ATTEMPTS) { attempt ->
            try {
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
                generationLogRecorder.record(
                    type = GenerationType.REPLY,
                    success = true,
                    attemptCount = attempt + 1,
                    latencyMs = System.currentTimeMillis() - startedAt,
                    memberId = memberId,
                    conversationId = conversationId,
                    usedTokens = output.usedTokens,
                    cachedTokens = output.cachedTokens,
                    inputTokens = output.inputTokens,
                    outputTokens = output.outputTokens,
                )
                return output
            } catch (e: CommentGenerationFailedException) {
                lastError = e
                log.warn("답글 생성 {}차 시도 실패: {}", attempt + 1, e.message)
            } catch (e: Exception) {
                // 재시도 대상이 아닌 예외: 실패로 기록한 뒤 즉시 던진다(재시도하지 않음).
                generationLogRecorder.record(
                    type = GenerationType.REPLY,
                    success = false,
                    attemptCount = attempt + 1,
                    latencyMs = System.currentTimeMillis() - startedAt,
                    memberId = memberId,
                    conversationId = conversationId,
                    failureReason = failureReasonOf(e),
                )
                throw e
            }
        }
        generationLogRecorder.record(
            type = GenerationType.REPLY,
            success = false,
            attemptCount = LlmRetryPolicy.MAX_ATTEMPTS,
            latencyMs = System.currentTimeMillis() - startedAt,
            memberId = memberId,
            conversationId = conversationId,
            usedTokens = lastError?.usedTokens,
            cachedTokens = lastError?.cachedTokens,
            inputTokens = lastError?.inputTokens,
            outputTokens = lastError?.outputTokens,
            failureReason = failureReasonOf(lastError),
        )
        throw checkNotNull(lastError)
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
    ): CommentGenerationOutput {
        val selection = characterSelector.select()
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
        var lastError: CommentGenerationFailedException? = null
        repeat(LlmRetryPolicy.MAX_ATTEMPTS) { attempt ->
            try {
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
                generationLogRecorder.record(
                    type = GenerationType.COMMENT,
                    success = true,
                    attemptCount = attempt + 1,
                    latencyMs = System.currentTimeMillis() - startedAt,
                    memberId = memberId,
                    conversationId = conversationId,
                    usedTokens = output.usedTokens,
                    cachedTokens = output.cachedTokens,
                    inputTokens = output.inputTokens,
                    outputTokens = output.outputTokens,
                )
                return output
            } catch (e: CommentGenerationFailedException) {
                lastError = e
                log.warn("댓글 생성 {}차 시도 실패: {}", attempt + 1, e.message)
            } catch (e: Exception) {
                // 재시도 대상이 아닌 예외: 실패로 기록한 뒤 즉시 던진다(재시도하지 않음).
                generationLogRecorder.record(
                    type = GenerationType.COMMENT,
                    success = false,
                    attemptCount = attempt + 1,
                    latencyMs = System.currentTimeMillis() - startedAt,
                    memberId = memberId,
                    conversationId = conversationId,
                    failureReason = failureReasonOf(e),
                )
                throw e
            }
        }
        generationLogRecorder.record(
            type = GenerationType.COMMENT,
            success = false,
            attemptCount = LlmRetryPolicy.MAX_ATTEMPTS,
            latencyMs = System.currentTimeMillis() - startedAt,
            memberId = memberId,
            conversationId = conversationId,
            usedTokens = lastError?.usedTokens,
            cachedTokens = lastError?.cachedTokens,
            inputTokens = lastError?.inputTokens,
            outputTokens = lastError?.outputTokens,
            failureReason = failureReasonOf(lastError),
        )
        throw checkNotNull(lastError)
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
                    messageRepository.findAllByRootMessageIdOrderByIdAsc(messageId),
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
