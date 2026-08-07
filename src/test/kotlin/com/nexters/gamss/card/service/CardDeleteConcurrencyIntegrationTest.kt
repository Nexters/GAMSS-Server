package com.nexters.gamss.card.service

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 같은 카드에 삭제 요청이 동시에 들어와도 한 번만 성공하는지 검증한다.
 *
 * [CardRepository.findByIdForUpdate] 가 PESSIMISTIC_WRITE 로 행을 잠그므로, 뒤이은 요청은
 * 앞선 삭제가 커밋될 때까지 대기했다가 깨어나 `deletedAt` 이 채워진 상태를 다시 읽고
 * CARD_ALREADY_DELETED 로 실패해야 한다. 락이 없다면 둘 다 '아직 안 지워짐' 을 읽어
 * 모두 성공하고, 삭제 시각도 늦게 커밋된 쪽으로 덮인다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class CardDeleteConcurrencyIntegrationTest {
    @Autowired
    private lateinit var cardService: CardService

    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @AfterEach
    fun cleanUp() {
        // 실제 커밋으로 락 경합을 재현해야 해서 @Transactional 롤백을 쓸 수 없으므로 직접 정리한다.
        cardRepository.deleteAll()
        conversationRepository.deleteAll()
    }

    @Test
    fun `같은 카드를 동시에 삭제해도 한 번만 성공한다`() {
        val memberId = 1L
        // 카드 삭제가 대화방까지 지우므로(CardService.deleteConversationOf) 실제 대화방이 있어야 한다.
        val conversation = conversationRepository.save(Conversation(memberId).apply { end() })
        val card =
            cardRepository.save(
                Card(
                    memberId = memberId,
                    conversationId = conversation.id,
                    emotion = EmotionType.ANGER,
                    summary = "동시 삭제 대상",
                    message = "얘 오늘 건들면 안 됨.",
                    conversationCreatedAt = Instant.now(),
                ),
            )

        val threadCount = 4
        val startLine = CyclicBarrier(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        var successCount = 0

        val futures =
            (1..threadCount).map {
                executor.submit {
                    startLine.await() // 모든 스레드를 동시에 출발시켜 경합을 유도한다
                    try {
                        cardService.deleteCard(memberId, card.id)
                        synchronized(this) { successCount++ }
                    } catch (e: Throwable) {
                        failures.add(e)
                    }
                }
            }
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(1, successCount, "삭제는 한 번만 성공해야 한다")
        assertEquals(threadCount - 1, failures.size)
        assertTrue(
            failures.all { it is BusinessException && it.errorCode == ErrorCode.CARD_ALREADY_DELETED },
            "나머지는 CARD_ALREADY_DELETED 로 실패해야 한다: $failures",
        )
        assertNotNull(cardRepository.findById(card.id).orElseThrow().deletedAt)
        assertTrue(
            conversationRepository.findById(conversation.id).orElseThrow().isDeleted(),
            "카드와 함께 대화방도 삭제돼야 한다",
        )
    }

    /**
     * 단건 삭제(행 잠금)와 벌크 삭제(조건부 UPDATE)가 같은 카드를 동시에 건드리는 경로 검증.
     *
     * 단건이 먼저 커밋되면 벌크의 `deletedAt is null` UPDATE 가 0건으로 끝나고, 벌크가 먼저
     * 커밋되면 단건이 CARD_ALREADY_DELETED 로 실패해야 한다 — 어느 쪽이든 삭제는 정확히 한 번만
     * 집계된다. 두 경로가 카드 → 대화방 순서로 행을 잡으므로 데드락도 없어야 한다
     * ([CardService.deleteCardsWithConversations] KDoc 의 잠금 순서 설명).
     */
    @Test
    fun `단건 삭제와 벌크 삭제가 같은 카드에 동시에 들어와도 삭제는 한 번만 집계된다`() {
        val memberId = 1L
        val conversation = conversationRepository.save(Conversation(memberId).apply { end() })
        val card =
            cardRepository.save(
                Card(
                    memberId = memberId,
                    conversationId = conversation.id,
                    emotion = EmotionType.ANGER,
                    summary = "단건·벌크 동시 삭제 대상",
                    message = "오늘은 다들 나만 찾네.",
                    conversationCreatedAt = Instant.now(),
                ),
            )
        val unexpectedFailures = Collections.synchronizedList(mutableListOf<Throwable>())
        // 각 작업은 자신이 지운 카드 수를 돌려준다. 단건 삭제의 CARD_ALREADY_DELETED 는
        // 경합에서 진 정상 결과라 0으로 친다.
        val deleteOnce: () -> Int = {
            try {
                cardService.deleteCard(memberId, card.id)
                1
            } catch (e: BusinessException) {
                if (e.errorCode != ErrorCode.CARD_ALREADY_DELETED) {
                    unexpectedFailures.add(e)
                }
                0
            }
        }
        val tasks: List<() -> Int> =
            listOf(
                deleteOnce,
                deleteOnce,
                { cardService.deleteAllCards(memberId) },
                { cardService.deleteCardsByEmotion(memberId, EmotionType.ANGER) },
            )

        val startLine = CyclicBarrier(tasks.size)
        val executor = Executors.newFixedThreadPool(tasks.size)
        val futures =
            tasks.map { task ->
                executor.submit<Int> {
                    startLine.await() // 모든 스레드를 동시에 출발시켜 경합을 유도한다
                    try {
                        task()
                    } catch (e: Throwable) {
                        unexpectedFailures.add(e)
                        0
                    }
                }
            }
        val deletedTotal = futures.sumOf { it.get(30, TimeUnit.SECONDS) }
        executor.shutdown()

        assertTrue(unexpectedFailures.isEmpty(), "예상 밖 실패(데드락 등)가 없어야 한다: $unexpectedFailures")
        assertEquals(1, deletedTotal, "네 경로가 동시에 지워도 삭제는 한 번만 집계돼야 한다")
        assertNotNull(cardRepository.findById(card.id).orElseThrow().deletedAt)
        assertTrue(
            conversationRepository.findById(conversation.id).orElseThrow().isDeleted(),
            "카드와 함께 대화방도 삭제돼야 한다",
        )
    }
}
