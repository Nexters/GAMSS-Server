package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals

/**
 * [MessageRepositoryTransactionTest]와 같은 이유로 클래스 레벨 트랜잭션 롤백을 쓰지 않는다 —
 * 서비스 트랜잭션 없이 리포지토리를 직접 호출하는 실제 운영 경로(CardService, PendingCardCleanupScheduler)를
 * 재현하기 위함이다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class ConversationRepositoryTransactionTest {
    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @AfterEach
    fun cleanUp() {
        conversationRepository.deleteAll()
    }

    @Test
    fun `서비스 트랜잭션 없이 직접 호출해도 대화 요약이 원자적으로 갱신된다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))

        val updatedCount = conversationRepository.updateSummary(conversation.id, "요약본")

        assertEquals(1, updatedCount)
        assertEquals("요약본", conversationRepository.findById(conversation.id).get().summary)
    }

    @Test
    fun `서비스 트랜잭션 없이 직접 호출해도 카드 생성 선점이 원자적으로 동작한다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))

        val firstClaim =
            conversationRepository.updateCardGenerationStatus(
                conversation.id,
                CardGenerationStatus.PENDING,
                listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED),
                Instant.now(),
            )
        val secondClaim =
            conversationRepository.updateCardGenerationStatus(
                conversation.id,
                CardGenerationStatus.PENDING,
                listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED),
                Instant.now(),
            )

        assertEquals(1, firstClaim)
        assertEquals(0, secondClaim)
        assertEquals(CardGenerationStatus.PENDING, conversationRepository.findById(conversation.id).get().cardGenerationStatus)
    }

    @Test
    fun `삭제된 채팅방은 카드 생성 선점이 되지 않는다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L).apply { delete() })

        val claimed =
            conversationRepository.updateCardGenerationStatus(
                conversation.id,
                CardGenerationStatus.PENDING,
                listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED),
                Instant.now(),
            )

        assertEquals(0, claimed)
        assertEquals(CardGenerationStatus.NONE, conversationRepository.findById(conversation.id).get().cardGenerationStatus)
    }

    @Test
    fun `카드 생성 FAILED 상태는 재선점된다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        conversationRepository.updateCardGenerationStatus(
            conversation.id,
            CardGenerationStatus.FAILED,
            listOf(CardGenerationStatus.NONE),
            Instant.now(),
        )

        val reclaimed =
            conversationRepository.updateCardGenerationStatus(
                conversation.id,
                CardGenerationStatus.PENDING,
                listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED),
                Instant.now(),
            )

        assertEquals(1, reclaimed)
    }

    @Test
    fun `서비스 트랜잭션 없이 직접 호출해도 오래된 카드 생성 PENDING이 리셋된다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        conversationRepository.updateCardGenerationStatus(
            conversation.id,
            CardGenerationStatus.PENDING,
            listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED),
            Instant.now().minusSeconds(600),
        )

        val resetCount = conversationRepository.resetStaleCardGenerationPending(Instant.now().minusSeconds(60))

        assertEquals(1, resetCount)
        assertEquals(CardGenerationStatus.NONE, conversationRepository.findById(conversation.id).get().cardGenerationStatus)
    }
}
