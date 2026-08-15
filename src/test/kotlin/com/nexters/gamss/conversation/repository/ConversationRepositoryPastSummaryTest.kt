package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.support.TestcontainersConfig
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [ConversationRepository.findRandomPastSummaries]가 실제 MySQL(RAND() LIMIT native query)에서 의도대로 동작하는지 검증한다. */
@SpringBootTest
@Import(TestcontainersConfig::class)
class ConversationRepositoryPastSummaryTest {
    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @AfterEach
    fun cleanUp() {
        conversationRepository.deleteAll()
    }

    /** 요약이 저장된 종료 대화방(과거 맥락 후보의 정상 형태). */
    private fun saveEndedWithSummary(
        memberId: Long,
        summary: String,
    ): Long {
        val saved = conversationRepository.save(Conversation(memberId).apply { end() })
        conversationRepository.updateSummary(saved.id, summary)
        return saved.id
    }

    @Test
    fun `과거 요약은 같은 회원의 최근 대화방 중 요약 있는 것만, 최대 pickCount개 무작위로 반환한다`() {
        val memberId = 1L
        val current = conversationRepository.save(Conversation(memberId))
        repeat(3) { saveEndedWithSummary(memberId, "요약-$it") }
        conversationRepository.save(Conversation(memberId).apply { end() }) // summary 없는 대화방 — 후보에서 제외돼야 함
        saveEndedWithSummary(memberId = 2L, summary = "다른 회원 요약")

        val picked = conversationRepository.findRandomPastSummaries(memberId, current.id, poolSize = 5, pickCount = 2)

        assertEquals(2, picked.size)
        assertTrue(picked.all { it.startsWith("요약-") })
    }

    @Test
    fun `poolSize를 넘는 오래된 대화방의 요약은 후보에서 제외된다`() {
        val memberId = 1L
        val current = conversationRepository.save(Conversation(memberId))
        repeat(6) { saveEndedWithSummary(memberId, "요약-$it") }

        // pickCount를 poolSize와 같게 줘서 무작위 선택 없이 풀 전체(최근 5개)를 그대로 확인한다.
        // "요약-0"이 가장 먼저 생성된(가장 오래된) 대화방이라 후보에서 빠져야 한다.
        val picked = conversationRepository.findRandomPastSummaries(memberId, current.id, poolSize = 5, pickCount = 5)

        assertEquals(setOf("요약-1", "요약-2", "요약-3", "요약-4", "요약-5"), picked.toSet())
    }

    @Test
    @Transactional
    fun `created_at이 동률이어도 id를 보조 정렬키로 써서 최근 5개가 결정론적으로 잘린다`() {
        val memberId = 1L
        val current = conversationRepository.save(Conversation(memberId))
        val ids = (0 until 6).map { saveEndedWithSummary(memberId, "요약-$it") }
        // 실제 운영에서 짧은 시간에 대화방이 여러 개 생기면 created_at이 같은 값으로 저장될 수 있다.
        // 이를 강제로 재현해 id desc 타이브레이커가 없으면 잘리는 5개가 매번 달라질 수 있는 상황을 만든다.
        entityManager.flush()
        val tiedInstant = Instant.parse("2026-01-01T00:00:00Z")
        ids.forEach { id ->
            entityManager
                .createQuery("update Conversation c set c.createdAt = :createdAt where c.id = :id")
                .setParameter("createdAt", tiedInstant)
                .setParameter("id", id)
                .executeUpdate()
        }
        entityManager.clear()

        val results =
            (1..5).map {
                conversationRepository.findRandomPastSummaries(memberId, current.id, poolSize = 5, pickCount = 5).toSet()
            }

        assertEquals(1, results.toSet().size) // 매 호출마다 같은 집합이 나와야 한다(결정론적).
        // id가 가장 작은(가장 먼저 생성된) "요약-0"이 항상 제외돼야 한다.
        assertEquals(setOf("요약-1", "요약-2", "요약-3", "요약-4", "요약-5"), results.first())
    }

    @Test
    fun `삭제된 대화방의 요약은 후보에서 제외된다`() {
        val memberId = 1L
        val current = conversationRepository.save(Conversation(memberId))
        val deleted = conversationRepository.save(Conversation(memberId).apply { delete() })
        conversationRepository.updateSummary(deleted.id, "삭제된 방 요약")
        saveEndedWithSummary(memberId, "종료된 방 요약")

        val picked = conversationRepository.findRandomPastSummaries(memberId, current.id, poolSize = 5, pickCount = 5)

        assertEquals(listOf("종료된 방 요약"), picked)
    }

    @Test
    fun `아직 진행 중인 대화방의 임시 요약은 후보에서 제외된다`() {
        val memberId = 1L
        val current = conversationRepository.save(Conversation(memberId))
        // 메시지를 보낼 때마다 저장되는 진행 중 요약 — 아직 끝나지 않은 오늘의 대화가
        // 다른 방의 "과거 맥락"으로 새어 들어가면 안 된다.
        val inProgress = conversationRepository.save(Conversation(memberId))
        conversationRepository.updateSummary(inProgress.id, "진행 중 방의 임시 요약")
        saveEndedWithSummary(memberId, "종료된 방 요약")

        val picked = conversationRepository.findRandomPastSummaries(memberId, current.id, poolSize = 5, pickCount = 5)

        assertEquals(listOf("종료된 방 요약"), picked)
    }

    @Test
    fun `과거 요약 풀이 pickCount보다 적으면 있는 만큼만 반환한다`() {
        val memberId = 1L
        val current = conversationRepository.save(Conversation(memberId))
        saveEndedWithSummary(memberId, "유일한 요약")

        val picked = conversationRepository.findRandomPastSummaries(memberId, current.id, poolSize = 5, pickCount = 2)

        assertEquals(listOf("유일한 요약"), picked)
    }

    @Test
    fun `현재 대화방 자신의 요약은 후보에서 제외된다`() {
        val memberId = 1L
        // 종료 상태로 둬야 status 필터가 아니라 자기 자신 제외 조건이 걸러낸 것임이 분명해진다.
        val current = conversationRepository.save(Conversation(memberId).apply { end() })
        conversationRepository.updateSummary(current.id, "현재 방 요약")

        val picked = conversationRepository.findRandomPastSummaries(memberId, current.id, poolSize = 5, pickCount = 2)

        assertEquals(emptyList(), picked)
    }
}
