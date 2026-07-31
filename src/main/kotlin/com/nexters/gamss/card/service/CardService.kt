package com.nexters.gamss.card.service

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.error.CardGenerationFailedException
import com.nexters.gamss.llm.generation.CardMessageGenerator
import com.nexters.gamss.llm.generation.CardMessageOutput
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.service.GenerationLogRecorder
import com.nexters.gamss.tokenlimit.service.DailyTokenLimitService
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 감정 카드 생성·조회. 카드는 종료된 대화 1개당 1개만 만들어지고(중복 생성 차단),
 * 캘린더 조회는 그 대화의 생성시간(KST) 기준으로 기존 대화 조회와 같은 날짜 경계를 따른다.
 */
@Service
class CardService(
    private val cardRepository: CardRepository,
    private val conversationRepository: ConversationRepository,
    private val cardMessageGenerator: CardMessageGenerator,
    private val generationLogRecorder: GenerationLogRecorder,
    private val dailyTokenLimitService: DailyTokenLimitService,
    private val cardPersistenceService: CardPersistenceService,
) {
    /**
     * 종료된 대화에 대해 대표 감정 캐릭터의 한 줄 대사를 생성해 카드를 저장한다. 외부 LLM 호출이 DB
     * 커넥션을 오래 점유하지 않도록 트랜잭션으로 감싸지 않는다.
     *
     * LLM을 부르기 전에 [Conversation.cardGenerationStatus]를 CAS로 선점한다([Message.commentStatus]와
     * 같은 패턴) — 동시 중복 요청은 선점에 실패해 LLM을 아예 호출하지 않고 즉시 반환된다.
     */
    fun createCard(
        memberId: Long,
        conversationId: Long,
        emotion: EmotionType,
        summary: String,
    ): Card {
        val conversation = claimForGeneration(conversationId, memberId)
        val output = generateMessage(emotion, summary, memberId, conversationId)
        val card =
            Card(
                memberId = memberId,
                conversationId = conversationId,
                emotion = emotion,
                summary = summary,
                message = output.message,
                conversationCreatedAt = conversation.createdAt,
            )
        return persistCard(card, conversationId, summary)
    }

    /** 카드를 저장하고, 저장 시점에 드러난 CAS 경합을 원인에 맞는 [BusinessException]으로 변환한다. */
    private fun persistCard(
        card: Card,
        conversationId: Long,
        summary: String,
    ): Card =
        try {
            cardPersistenceService.save(card, conversationId, summary)
        } catch (e: DataIntegrityViolationException) {
            if (cardRepository.existsByConversationId(conversationId)) {
                // 카드는 이미 다른 요청이 저장을 마쳤다는 뜻이라 DONE으로 맞춰준다 — FAILED로 두면
                // 재선점 때마다 LLM을 다시 부르고도 매번 같은 유니크 제약에 걸려 낭비만 반복된다.
                markCardGenerationStatus(conversationId, CardGenerationStatus.DONE)
                throw BusinessException(ErrorCode.CARD_ALREADY_EXISTS, e.message).apply { initCause(e) }
            }
            markCardGenerationStatus(conversationId, CardGenerationStatus.FAILED)
            throw e
        } catch (e: CardGenerationStateConflictException) {
            // updated == 0이 나온 시점엔 이미 PENDING이 아니라는 뜻이라 markCardGenerationStatus로
            // 되돌릴 대상 자체가 없다 — 채팅방 삭제(status <> DELETED 조건 탈락) 아니면 정리
            // 스케줄러가 이미 PENDING을 NONE으로 되돌린 상태다.
            val conversation = conversationRepository.findById(conversationId).orElse(null)
            if (conversation?.status == ConversationStatus.DELETED) {
                throw BusinessException(ErrorCode.CONVERSATION_ALREADY_DELETED).apply { initCause(e) }
            }
            throw BusinessException(ErrorCode.CARD_GENERATION_FAILED, e.message).apply { initCause(e) }
        }

    /** 소유권·종료 상태·토큰 상한을 확인한 뒤 [CardGenerationStatus]를 CAS로 선점한다. */
    private fun claimForGeneration(
        conversationId: Long,
        memberId: Long,
    ): Conversation {
        val conversation = getOwnedConversation(conversationId, memberId)
        // 종료 후 삭제된 방은 status가 DELETED로 덮어써져 ENDED 여부가 사라지므로, 삭제 여부를 먼저
        // 확인해야 "종료되지 않았다"는 정반대 안내가 나가지 않는다.
        conversation.ensureNotDeleted()
        if (conversation.status != ConversationStatus.ENDED) {
            throw BusinessException(ErrorCode.CONVERSATION_NOT_ENDED)
        }
        if (!dailyTokenLimitService.isWithinLimit(memberId)) {
            throw BusinessException(ErrorCode.DAILY_TOKEN_LIMIT_EXCEEDED)
        }

        val claimed =
            conversationRepository.updateCardGenerationStatus(
                conversationId,
                CardGenerationStatus.PENDING,
                listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED),
                Instant.now(),
            )
        if (claimed == 0) {
            if (cardRepository.existsByConversationId(conversationId)) {
                throw BusinessException(ErrorCode.CARD_ALREADY_EXISTS)
            }
            throw BusinessException(ErrorCode.CARD_GENERATION_IN_PROGRESS)
        }
        return conversation
    }

    /** LLM으로 카드 대사를 생성하고 생성 로그를 남긴다. 실패 시 상태를 FAILED로 되돌린 뒤 예외로 변환한다. */
    private fun generateMessage(
        emotion: EmotionType,
        summary: String,
        memberId: Long,
        conversationId: Long,
    ): CardMessageOutput {
        val startedAt = System.currentTimeMillis()
        val output =
            try {
                cardMessageGenerator.generate(emotion, summary)
            } catch (e: CardGenerationFailedException) {
                generationLogRecorder.record(
                    type = GenerationType.CARD,
                    success = false,
                    attemptCount = 1,
                    latencyMs = System.currentTimeMillis() - startedAt,
                    memberId = memberId,
                    conversationId = conversationId,
                    usedTokens = e.usedTokens,
                    cachedTokens = e.cachedTokens,
                    inputTokens = e.inputTokens,
                    outputTokens = e.outputTokens,
                    failureReason = (e.cause ?: e).javaClass.simpleName,
                )
                markCardGenerationStatus(conversationId, CardGenerationStatus.FAILED)
                throw BusinessException(ErrorCode.CARD_GENERATION_FAILED, e.message).apply { initCause(e) }
            }
        generationLogRecorder.record(
            type = GenerationType.CARD,
            success = true,
            attemptCount = 1,
            latencyMs = System.currentTimeMillis() - startedAt,
            memberId = memberId,
            conversationId = conversationId,
            usedTokens = output.usedTokens,
            cachedTokens = output.cachedTokens,
            inputTokens = output.inputTokens,
            outputTokens = output.outputTokens,
        )
        return output
    }

    private fun markCardGenerationStatus(
        conversationId: Long,
        status: CardGenerationStatus,
    ) {
        val updated =
            conversationRepository.updateCardGenerationStatus(
                conversationId,
                status,
                listOf(CardGenerationStatus.PENDING),
                Instant.now(),
            )
        if (updated == 0) {
            log.warn("카드 생성 상태 전이 실패: conversationId={}, to={} (이미 PENDING 상태가 아님)", conversationId, status)
        }
    }

    /** 날짜(KST 자정~자정)에 속한 카드를 조회한다. */
    @Transactional(readOnly = true)
    fun getCardsByDate(
        memberId: Long,
        date: LocalDate,
    ): List<Card> {
        val start = date.atStartOfDay(ZONE).toInstant()
        val end = date.plusDays(1).atStartOfDay(ZONE).toInstant()
        return cardRepository.findAllByMemberIdAndConversationCreatedAtInRange(memberId, start, end)
    }

    /** 월(KST) 전체에 속한 카드를 조회한다(캘린더용). */
    @Transactional(readOnly = true)
    fun getCardsByMonth(
        memberId: Long,
        yearMonth: YearMonth,
    ): List<Card> {
        val firstDay = yearMonth.atDay(1)
        val nextMonthFirstDay = yearMonth.plusMonths(1).atDay(1)
        val start = firstDay.atStartOfDay(ZONE).toInstant()
        val end = nextMonthFirstDay.atStartOfDay(ZONE).toInstant()
        return cardRepository.findAllByMemberIdAndConversationCreatedAtInRange(memberId, start, end)
    }

    private fun getOwnedConversation(
        conversationId: Long,
        memberId: Long,
    ): Conversation {
        val conversation =
            conversationRepository
                .findById(conversationId)
                .orElseThrow { BusinessException(ErrorCode.CONVERSATION_NOT_FOUND) }
        if (!conversation.isOwnedBy(memberId)) {
            throw BusinessException(ErrorCode.CONVERSATION_ACCESS_DENIED)
        }
        return conversation
    }

    companion object {
        private val ZONE = ZoneId.of("Asia/Seoul")
        private val log = LoggerFactory.getLogger(CardService::class.java)
    }
}
