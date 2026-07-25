package com.nexters.gamss.card.repository

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CardRepositoryTest : RepositoryTest() {
    @Autowired
    lateinit var cardRepository: CardRepository

    @Autowired
    lateinit var conversationRepository: ConversationRepository

    private fun card(
        conversationId: Long,
        createdAt: Instant,
    ): Card =
        Card(
            memberId = MEMBER_ID,
            conversationId = conversationId,
            emotion = EmotionType.JOY,
            summary = "요약",
            message = "대사",
            conversationCreatedAt = createdAt,
        )

    @Test
    fun `삭제된 채팅방의 카드는 날짜·월별 조회에서 제외된다`() {
        val active = conversationRepository.save(Conversation(MEMBER_ID))
        val deleted = conversationRepository.save(Conversation(MEMBER_ID).apply { delete() })
        val createdAt = Instant.parse("2026-07-20T00:00:00Z")
        cardRepository.save(card(active.id, createdAt))
        cardRepository.save(card(deleted.id, createdAt))

        val found =
            cardRepository.findAllByMemberIdAndConversationCreatedAtInRange(
                MEMBER_ID,
                createdAt.minusSeconds(60),
                createdAt.plusSeconds(60),
            )

        assertEquals(1, found.size)
        assertEquals(active.id, found.first().conversationId)
    }

    @Test
    fun `종료된(삭제되지 않은) 채팅방의 카드는 정상적으로 조회된다`() {
        val ended = conversationRepository.save(Conversation(MEMBER_ID).apply { end() })
        val createdAt = Instant.parse("2026-07-20T00:00:00Z")
        cardRepository.save(card(ended.id, createdAt))

        val found =
            cardRepository.findAllByMemberIdAndConversationCreatedAtInRange(
                MEMBER_ID,
                createdAt.minusSeconds(60),
                createdAt.plusSeconds(60),
            )

        assertEquals(1, found.size)
        assertEquals(ended.id, found.first().conversationId)
    }

    private companion object {
        const val MEMBER_ID = 1L
    }
}
