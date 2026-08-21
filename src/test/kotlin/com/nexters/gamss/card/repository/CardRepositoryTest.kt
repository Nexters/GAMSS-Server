package com.nexters.gamss.card.repository

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.domain.ShareToken
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `공유 토큰으로 카드를 연다`() {
        val conversation = conversationRepository.save(Conversation(MEMBER_ID).apply { end() })
        val saved = cardRepository.save(card(conversation.id, CREATED_AT).apply { share(ShareToken(TOKEN)) })

        val found = cardRepository.findVisibleByShareToken(TOKEN)

        assertEquals(saved.id, found.get().id)
    }

    /**
     * 링크는 한 번 나가면 회수할 수 없다. 가시성 조건이 [CardRepository.findVisibleById] 와
     * 갈리면 사용자가 지운 카드가 링크로는 계속 열린다.
     */
    @Test
    fun `지운 카드는 공유 토큰으로도 열리지 않는다`() {
        val conversation = conversationRepository.save(Conversation(MEMBER_ID).apply { end() })
        cardRepository.save(
            card(conversation.id, CREATED_AT).apply {
                share(ShareToken(TOKEN))
                delete()
            },
        )

        assertTrue(cardRepository.findVisibleByShareToken(TOKEN).isEmpty)
    }

    @Test
    fun `삭제된 채팅방의 카드는 공유 토큰으로도 열리지 않는다`() {
        val conversation = conversationRepository.save(Conversation(MEMBER_ID).apply { delete() })
        cardRepository.save(card(conversation.id, CREATED_AT).apply { share(ShareToken(TOKEN)) })

        assertTrue(cardRepository.findVisibleByShareToken(TOKEN).isEmpty)
    }

    /**
     * share_token 은 utf8mb4_bin 이라 대소문자를 구분한다(V35). 서버 기본 collation 을 따르면
     * 아래 두 토큰이 **같은 유니크 키**가 되어 둘째 저장이 제약에 걸리고, 걸리지 않더라도 조회가
     * 남의 카드를 맞다고 돌려준다.
     */
    @Test
    fun `대소문자만 다른 공유 토큰은 서로 다른 카드다`() {
        val lower = conversationRepository.save(Conversation(MEMBER_ID).apply { end() })
        val upper = conversationRepository.save(Conversation(MEMBER_ID).apply { end() })
        val lowerCard = cardRepository.save(card(lower.id, CREATED_AT).apply { share(ShareToken(MIXED_CASE_TOKEN)) })
        val upperCard =
            cardRepository.save(card(upper.id, CREATED_AT).apply { share(ShareToken(MIXED_CASE_TOKEN.swapCase())) })
        cardRepository.flush()

        assertEquals(lowerCard.id, cardRepository.findVisibleByShareToken(MIXED_CASE_TOKEN).get().id)
        assertEquals(upperCard.id, cardRepository.findVisibleByShareToken(MIXED_CASE_TOKEN.swapCase()).get().id)
    }

    private fun String.swapCase(): String = map { if (it.isUpperCase()) it.lowercaseChar() else it.uppercaseChar() }.joinToString("")

    private companion object {
        const val MEMBER_ID = 1L
        const val OTHER_MEMBER_ID = 2L
        val CREATED_AT: Instant = Instant.parse("2026-07-20T00:00:00Z")
        const val TOKEN = "Zm9vYmFyYmF6cXV4MTIzNA"
        const val MIXED_CASE_TOKEN = "aBcDeFgHiJkLmNoPqRsTuV"
    }
}
