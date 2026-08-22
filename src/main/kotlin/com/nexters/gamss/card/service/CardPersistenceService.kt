package com.nexters.gamss.card.service

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.repository.ConversationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * LLM 호출까지 끝난 카드를 실제로 저장한다. 별도 빈으로 분리한 이유는 [CardService]가 자기 자신을
 * 호출(self-invocation)하면 @Transactional 프록시를 우회해 트랜잭션이 걸리지 않기 때문이다
 * ([CommentPersistenceService]와 같은 이유).
 */
@Service
class CardPersistenceService(
    private val cardRepository: CardRepository,
    private val conversationRepository: ConversationRepository,
) {
    /**
     * 카드 저장 · 대화 요약 저장 · 카드 생성 상태 DONE 마킹을 하나의 트랜잭션으로 묶는다. 저장 시점의
     * 유니크 제약 위반은 이 트랜잭션이 롤백된 뒤 [CardService]가 별도로 처리한다.
     *
     * [summary]가 null이면 대화방 요약은 건드리지 않는다 — 클라이언트 요약 없이 만들어진 카드라
     * 남길 원본이 없는 경우다([CardService.createCard]).
     */
    @Transactional
    fun save(
        card: Card,
        conversationId: Long,
        summary: String?,
    ): Card {
        val saved = cardRepository.saveAndFlush(card)
        summary?.let { conversationRepository.updateSummary(conversationId, it) }
        val updated =
            conversationRepository.updateCardGenerationStatus(
                conversationId,
                CardGenerationStatus.DONE,
                listOf(CardGenerationStatus.PENDING),
                Instant.now(),
            )
        if (updated != 1) {
            throw CardGenerationStateConflictException(
                "카드 생성 상태 전이가 실패했습니다. conversationId=$conversationId",
            )
        }
        return saved
    }
}
