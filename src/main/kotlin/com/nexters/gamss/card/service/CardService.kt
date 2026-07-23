package com.nexters.gamss.card.service

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.CardGenerationFailedException
import com.nexters.gamss.llm.CardMessageGenerator
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
) {
    /**
     * 종료된 대화에 대해 대표 감정 캐릭터의 한 줄 대사를 생성해 카드를 저장한다.
     * 외부 LLM 호출이 DB 커넥션을 오래 점유하지 않도록 이 메서드는 트랜잭션으로 감싸지 않는다 —
     * 조회·검사는 각 리포지토리 호출 단위 트랜잭션으로 처리하고, 최종 저장은 conversation_id
     * 유니크 제약으로 동시 생성의 원자성을 보장한다(사전 검사를 함께 통과한 경쟁 요청은 저장에서 걸러짐).
     */
    fun createCard(
        memberId: Long,
        conversationId: Long,
        emotion: EmotionType,
        summary: String,
    ): Card {
        val conversation = getOwnedConversation(conversationId, memberId)
        if (conversation.status != ConversationStatus.ENDED) {
            throw BusinessException(ErrorCode.CONVERSATION_NOT_ENDED)
        }
        if (cardRepository.existsByConversationId(conversationId)) {
            throw BusinessException(ErrorCode.CARD_ALREADY_EXISTS)
        }
        val output =
            try {
                cardMessageGenerator.generate(emotion, summary)
            } catch (e: CardGenerationFailedException) {
                throw BusinessException(ErrorCode.CARD_GENERATION_FAILED, e.message)
            }
        val card =
            Card(
                memberId = memberId,
                conversationId = conversationId,
                emotion = emotion,
                summary = summary,
                message = output.message,
                conversationCreatedAt = conversation.createdAt,
            )
        return try {
            cardRepository.saveAndFlush(card)
        } catch (e: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.CARD_ALREADY_EXISTS, e.message)
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
    }
}
