package com.nexters.gamss.card.service

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 서비스 트랜잭션이 실제로 롤백되는지 직접 확인해야 해서 클래스 레벨 트랜잭션을 쓰지 않는다
 * ([ConversationRepositoryTransactionTest]와 같은 이유).
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class CardPersistenceServiceTest {
    @Autowired
    private lateinit var cardPersistenceService: CardPersistenceService

    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @AfterEach
    fun cleanUp() {
        cardRepository.deleteAll()
        conversationRepository.deleteAll()
    }

    @Test
    fun `카드 생성 상태 전이가 실패하면 이미 flush된 카드 저장도 함께 롤백된다`() {
        val card =
            Card(
                memberId = 1L,
                conversationId = NON_EXISTENT_CONVERSATION_ID,
                emotion = EmotionType.ANGER,
                summary = "요약",
                message = "대사",
                conversationCreatedAt = Instant.now(),
            )

        // 존재하지 않는 대화라 updateCardGenerationStatus가 0건 갱신 → check() 실패로 트랜잭션이 롤백된다.
        assertFailsWith<IllegalStateException> {
            cardPersistenceService.save(card, NON_EXISTENT_CONVERSATION_ID, "요약")
        }

        assertEquals(0, cardRepository.count())
    }

    private companion object {
        const val NON_EXISTENT_CONVERSATION_ID = 999_999L
    }
}
