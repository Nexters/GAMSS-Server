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
import com.nexters.gamss.llm.generation.ReplyGenerationOutput
import com.nexters.gamss.llm.parsing.CommentFeedValidator
import com.nexters.gamss.llm.prompt.PromptCharacterId
import com.nexters.gamss.llm.selection.CharacterSelector
import com.nexters.gamss.llm.selection.EongttungTopicSelector
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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generateComments(
        memberId: Long,
        messageId: Long,
    ): CommentGenerationResult {
        val rootMessage = getOwnedRootMessage(memberId, messageId)

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
            val output = generateWithRetry(rootMessage.content)
            val saved = commentPersistenceService.saveFeed(rootMessage.conversationId, messageId, output.feed)
            CommentGenerationResult(CommentGenerationOutcome.DONE, saved, output.usedTokens)
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
            CommentGenerationResult(CommentGenerationOutcome.FAILED)
        }
    }

    fun generateReplyComment(
        memberId: Long,
        messageId: Long,
    ): ReplyGenerationResult {
        val userReplyMessage = getOwnedRootMessage(memberId, messageId)
        val characterMessage =
            messageRepository
                .findById(userReplyMessage.repliesToMessageId ?: throw BusinessException(ErrorCode.INVALID_COMMENT_TARGET))
                .orElseThrow { BusinessException(ErrorCode.MESSAGE_NOT_FOUND) }
        if (characterMessage.senderType != SenderType.CHARACTER) {
            throw BusinessException(ErrorCode.INVALID_COMMENT_TARGET)
        }
        val diaryMessage =
            messageRepository
                .findById(characterMessage.rootMessageId ?: throw BusinessException(ErrorCode.INVALID_COMMENT_TARGET))
                .orElseThrow { BusinessException(ErrorCode.MESSAGE_NOT_FOUND) }

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
            val output = generateReplyWithRetry(diaryMessage.content, characterMessage, userReplyMessage.content)
            val saved =
                commentPersistenceService.saveReply(
                    conversationId = userReplyMessage.conversationId,
                    rootMessageId = diaryMessage.id,
                    repliesToMessageId = messageId,
                    characterId = characterMessage.emotionType!!,
                    text = output.text,
                )
            ReplyGenerationResult(CommentGenerationOutcome.DONE, saved, output.usedTokens)
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
            ReplyGenerationResult(CommentGenerationOutcome.FAILED)
        }
    }

    /** LLM 호출 + 의미 검증을 하나의 단위로 묶어 최대 [MAX_ATTEMPTS]회 시도한다(DoD: 실패 시 1회 재시도). */
    private fun generateReplyWithRetry(
        diaryContent: String,
        characterMessage: Message,
        userReply: String,
    ): ReplyGenerationOutput {
        var lastError: CommentGenerationFailedException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val output =
                    commentGenerator.generateReply(
                        diaryContent,
                        PromptCharacterId.of(characterMessage.emotionType!!).promptId,
                        characterMessage.content,
                        userReply,
                    )
                commentFeedValidator.validateReply(output.text)
                return output
            } catch (e: CommentGenerationFailedException) {
                lastError = e
                log.warn("답글 생성 {}차 시도 실패: {}", attempt + 1, e.message)
            }
        }
        throw checkNotNull(lastError)
    }

    /** LLM 호출 + 의미 검증을 하나의 단위로 묶어 최대 [MAX_ATTEMPTS]회 시도한다(DoD: 실패 시 1회 재시도). */
    private fun generateWithRetry(diaryContent: String): CommentGenerationOutput {
        val characters = characterSelector.select()
        val tikitakaCount = characterSelector.selectTikitakaCount()
        val eongttungTopic = if (EmotionType.QUIRKY in characters) eongttungTopicSelector.select() else null
        // TODO: 프론트에서 과거 요약을 내려주기 전까지는 항상 빈 값으로 호출한다.
        val pastSummary = ""

        var lastError: CommentGenerationFailedException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val output =
                    commentGenerator.generateComment(pastSummary, diaryContent, characters, tikitakaCount, eongttungTopic)
                commentFeedValidator.validate(output.feed, characters, tikitakaCount)
                return output
            } catch (e: CommentGenerationFailedException) {
                lastError = e
                log.warn("댓글 생성 {}차 시도 실패: {}", attempt + 1, e.message)
            }
        }
        throw checkNotNull(lastError)
    }

    private fun currentStatusResult(messageId: Long): CommentGenerationResult {
        val message =
            messageRepository
                .findById(messageId)
                .orElseThrow { BusinessException(ErrorCode.MESSAGE_NOT_FOUND) }
        return when (message.commentStatus) {
            CommentStatus.DONE -> {
                CommentGenerationResult(
                    CommentGenerationOutcome.DONE,
                    messageRepository.findAllByRootMessageIdOrderByIdAsc(messageId),
                )
            }

            else -> {
                CommentGenerationResult(CommentGenerationOutcome.GENERATING)
            }
        }
    }

    private fun currentReplyStatusResult(messageId: Long): ReplyGenerationResult {
        val message =
            messageRepository
                .findById(messageId)
                .orElseThrow { BusinessException(ErrorCode.MESSAGE_NOT_FOUND) }
        if (message.commentStatus != CommentStatus.DONE) {
            return ReplyGenerationResult(CommentGenerationOutcome.GENERATING)
        }
        val reply =
            messageRepository.findByRepliesToMessageId(messageId)
                ?: run {
                    log.error("commentStatus는 DONE인데 답글 메시지를 찾을 수 없습니다. messageId={}", messageId)
                    return ReplyGenerationResult(CommentGenerationOutcome.FAILED)
                }
        return ReplyGenerationResult(CommentGenerationOutcome.DONE, reply)
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

    companion object {
        private const val MAX_ATTEMPTS = 2
    }
}
