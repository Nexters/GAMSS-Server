package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.CommentStatus
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.CharacterSelector
import com.nexters.gamss.llm.CommentFeed
import com.nexters.gamss.llm.CommentFeedValidator
import com.nexters.gamss.llm.CommentGenerationFailedException
import com.nexters.gamss.llm.CommentGenerator
import com.nexters.gamss.llm.EongttungTopicSelector
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 선점(CAS) -> LLM 호출+검증(트랜잭션 밖, 최대 2회) -> 저장 을 오케스트레이션한다.
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
            val feed = generateWithRetry(rootMessage.content)
            val saved = commentPersistenceService.saveFeed(rootMessage.conversationId, messageId, feed)
            CommentGenerationResult(CommentGenerationOutcome.DONE, saved)
        } catch (e: CommentGenerationFailedException) {
            log.warn("댓글 생성 최종 실패 messageId={}", messageId, e)
            messageRepository.updateCommentStatus(
                messageId,
                CommentStatus.FAILED,
                listOf(CommentStatus.PENDING),
                Instant.now(),
            )
            CommentGenerationResult(CommentGenerationOutcome.FAILED)
        }
    }

    /** LLM 호출 + 의미 검증을 하나의 단위로 묶어 최대 [MAX_ATTEMPTS]회 시도한다(DoD: 실패 시 1회 재시도). */
    private fun generateWithRetry(diaryContent: String): CommentFeed {
        val characters = characterSelector.select()
        val tikitakaCount = characterSelector.selectTikitakaCount()
        val eongttungTopic = if (EmotionType.QUIRKY in characters) eongttungTopicSelector.select() else null
        // TODO: 프론트에서 과거 요약을 내려주기 전까지는 항상 빈 값으로 호출한다.
        val pastSummary = ""

        var lastError: CommentGenerationFailedException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val feed = commentGenerator.generate(pastSummary, diaryContent, characters, tikitakaCount, eongttungTopic)
                commentFeedValidator.validate(feed, characters, tikitakaCount)
                return feed
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
        return message
    }

    companion object {
        private const val MAX_ATTEMPTS = 2
    }
}
