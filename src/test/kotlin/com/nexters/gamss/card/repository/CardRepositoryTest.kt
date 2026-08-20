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
        emotion: EmotionType = EmotionType.JOY,
        memberId: Long = MEMBER_ID,
    ): Card =
        Card(
            memberId = memberId,
            conversationId = conversationId,
            emotion = emotion,
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

    @Test
    fun `감정별 조회는 다른 감정의 카드를 걸러낸다`() {
        val anger = conversationRepository.save(Conversation(MEMBER_ID))
        val joy = conversationRepository.save(Conversation(MEMBER_ID))
        val createdAt = Instant.parse("2026-07-20T00:00:00Z")
        cardRepository.save(card(anger.id, createdAt, EmotionType.ANGER))
        cardRepository.save(card(joy.id, createdAt, EmotionType.JOY))

        val found =
            cardRepository.findAllByMemberIdAndEmotionAndConversationCreatedAtInRange(
                MEMBER_ID,
                EmotionType.ANGER,
                createdAt.minusSeconds(60),
                createdAt.plusSeconds(60),
            )

        assertEquals(1, found.size)
        assertEquals(anger.id, found.first().conversationId)
    }

    @Test
    fun `감정별 조회도 지운 카드와 삭제된 채팅방의 카드를 제외한다`() {
        // 조회·삭제 쿼리가 공유하는 가시성 규칙이라, 갈리면 캘린더에 없는 카드가 감정 탭에만 나타난다.
        val active = conversationRepository.save(Conversation(MEMBER_ID))
        val deletedConversation = conversationRepository.save(Conversation(MEMBER_ID).apply { delete() })
        val deletedCardConversation = conversationRepository.save(Conversation(MEMBER_ID))
        val createdAt = Instant.parse("2026-07-20T00:00:00Z")
        cardRepository.save(card(active.id, createdAt, EmotionType.ANGER))
        cardRepository.save(card(deletedConversation.id, createdAt, EmotionType.ANGER))
        cardRepository.save(card(deletedCardConversation.id, createdAt, EmotionType.ANGER).apply { delete() })

        val found =
            cardRepository.findAllByMemberIdAndEmotionAndConversationCreatedAtInRange(
                MEMBER_ID,
                EmotionType.ANGER,
                createdAt.minusSeconds(60),
                createdAt.plusSeconds(60),
            )

        assertEquals(1, found.size)
        assertEquals(active.id, found.first().conversationId)
    }

    @Test
    fun `감정별 조회는 최신순으로 돌려주고 같은 시각이면 id 내림차순이다`() {
        val older = conversationRepository.save(Conversation(MEMBER_ID))
        val newer = conversationRepository.save(Conversation(MEMBER_ID))
        val tiedFirst = conversationRepository.save(Conversation(MEMBER_ID))
        val tiedSecond = conversationRepository.save(Conversation(MEMBER_ID))
        val base = Instant.parse("2026-07-20T00:00:00Z")
        val olderCard = cardRepository.save(card(older.id, base, EmotionType.ANGER))
        val newerCard = cardRepository.save(card(newer.id, base.plusSeconds(60), EmotionType.ANGER))
        // 동률 타이브레이크를 보려면 conversationCreatedAt 이 정확히 같아야 한다.
        val tiedFirstCard = cardRepository.save(card(tiedFirst.id, base.plusSeconds(120), EmotionType.ANGER))
        val tiedSecondCard = cardRepository.save(card(tiedSecond.id, base.plusSeconds(120), EmotionType.ANGER))

        val found =
            cardRepository.findAllByMemberIdAndEmotionAndConversationCreatedAtInRange(
                MEMBER_ID,
                EmotionType.ANGER,
                base.minusSeconds(60),
                base.plusSeconds(180),
            )

        assertEquals(
            listOf(tiedSecondCard.id, tiedFirstCard.id, newerCard.id, olderCard.id),
            found.map { it.id },
        )
    }

    @Test
    fun `감정별 조회에 남의 카드는 섞이지 않는다`() {
        val mine = conversationRepository.save(Conversation(MEMBER_ID))
        val others = conversationRepository.save(Conversation(OTHER_MEMBER_ID))
        val createdAt = Instant.parse("2026-07-20T00:00:00Z")
        cardRepository.save(card(mine.id, createdAt, EmotionType.ANGER))
        cardRepository.save(card(others.id, createdAt, EmotionType.ANGER, memberId = OTHER_MEMBER_ID))

        val found =
            cardRepository.findAllByMemberIdAndEmotionAndConversationCreatedAtInRange(
                MEMBER_ID,
                EmotionType.ANGER,
                createdAt.minusSeconds(60),
                createdAt.plusSeconds(60),
            )

        assertEquals(1, found.size)
        assertEquals(mine.id, found.first().conversationId)
    }

    private companion object {
        const val MEMBER_ID = 1L
        const val OTHER_MEMBER_ID = 2L
    }
}
