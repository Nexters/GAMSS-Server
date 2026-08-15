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
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ConversationRepository.findAutoCardTargetIds]가 자동 카드 생성 배치의 대상만 집어오는지 검증한다.
 * 대상 선정이 틀리면 카드가 안 만들어지거나(누락) 이미 끝난 방에 헛일을 한다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class ConversationRepositoryAutoCardTargetTest {
    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @AfterEach
    fun cleanUp() {
        conversationRepository.deleteAll()
    }

    /** 지금 만든 방들이 모두 "그 전에 생성된" 것이 되도록 넉넉히 미래로 잡은 상한. */
    private val tomorrow: Instant = Instant.now().plus(1, ChronoUnit.DAYS)

    /** 지금 만든 방들이 모두 하한 안에 들도록 넉넉히 과거로 잡은 하한. */
    private val yesterday: Instant = Instant.now().minus(1, ChronoUnit.DAYS)

    private fun findTargets(
        createdAfter: Instant = yesterday,
        createdBefore: Instant = tomorrow,
    ): List<Long> = conversationRepository.findAutoCardTargetIds(createdAfter, createdBefore)

    private fun markCardGenerated(conversationId: Long) {
        conversationRepository.updateCardGenerationStatus(
            conversationId,
            CardGenerationStatus.DONE,
            listOf(CardGenerationStatus.NONE),
            Instant.now(),
        )
    }

    private fun markFailed(
        conversationId: Long,
        at: Instant,
    ) {
        conversationRepository.updateCardGenerationStatus(
            conversationId,
            CardGenerationStatus.FAILED,
            listOf(CardGenerationStatus.NONE),
            at,
        )
    }

    /** 요약이 저장된 방(카드를 만들 수 있는 상태). 요약은 메시지를 보낼 때마다 갱신된다. */
    private fun saveWithSummary(
        memberId: Long = 1L,
        ended: Boolean = false,
    ): Long {
        val saved = conversationRepository.save(Conversation(memberId).apply { if (ended) end() })
        conversationRepository.updateSummary(saved.id, "오늘 있었던 일")
        return saved.id
    }

    @Test
    fun `아직 카드가 없는 진행 중 대화방과 종료된 대화방을 모두 대상으로 삼는다`() {
        val active = saveWithSummary()
        // 종료까지만 되고 카드 생성에서 끊긴 방 — 다음 실행이 이어서 처리해야 한다.
        val endedWithoutCard = saveWithSummary(ended = true)

        assertEquals(listOf(active, endedWithoutCard), findTargets())
    }

    @Test
    fun `자동 생성을 포기한(SKIPPED) 대화방은 대상에서 빠진다`() {
        // 요약이 없어 포기한 방은 종료된 상태라 요약이 채워질 길이 없다 — 다시 집어도 결론이 같다.
        val skipped = saveWithSummary(ended = true)
        conversationRepository.updateCardGenerationStatus(
            skipped,
            CardGenerationStatus.SKIPPED,
            listOf(CardGenerationStatus.NONE),
            Instant.now(),
        )
        val pending = saveWithSummary()

        assertEquals(listOf(pending), findTargets())
    }

    @Test
    fun `실패한 대화방은 하루 안에 다시 시도하지 않는다`() {
        // 실패는 재시도가 살아 있어야 하므로 상태로 뺄 수 없다. 대신 마지막 시도가 이번 하루 안이면
        // 건너뛴다 — 영구적으로 실패하는 방이 생겨도 태우는 LLM 호출이 하루 한 번으로 묶인다.
        val boundary = Instant.now().plus(1, ChronoUnit.HOURS)
        val failedAfterBoundary = saveWithSummary(ended = true)
        val failedBeforeBoundary = saveWithSummary(ended = true)
        // 경계 이후에 실패했다 = 이번 하루 안에 이미 한 번 태웠다.
        markFailed(failedAfterBoundary, at = boundary.plusSeconds(60))
        markFailed(failedBeforeBoundary, at = boundary.minus(1, ChronoUnit.DAYS))

        assertEquals(listOf(failedBeforeBoundary), findTargets(createdBefore = boundary))
    }

    @Test
    fun `요약이 없는 대화방도 자동 종료 대상이다`() {
        // 카드는 못 만들지만 종료는 한다 — 어제자 진행 중인 방을 전부 닫는 것이 이 배치의 계약이다.
        val noSummary = conversationRepository.save(Conversation(memberId = 1L))
        val withSummary = saveWithSummary()

        assertEquals(listOf(noSummary.id, withSummary), findTargets())
    }

    @Test
    fun `이미 카드가 만들어진 대화방은 대상에서 빠진다`() {
        val done = saveWithSummary(ended = true)
        markCardGenerated(done)
        val pending = saveWithSummary()

        assertEquals(listOf(pending), findTargets())
    }

    @Test
    fun `삭제된 대화방은 대상에서 빠진다`() {
        val deleted = conversationRepository.save(Conversation(memberId = 1L).apply { delete() })
        conversationRepository.updateSummary(deleted.id, "삭제된 방 요약")
        val alive = saveWithSummary()

        assertEquals(listOf(alive), findTargets())
    }

    @Test
    fun `상한 이후에 만들어진 대화방은 대상에서 빠진다`() {
        saveWithSummary()

        // 아직 지나지 않은 하루에 속한 방은 대상이 아니다 — 상한은 가장 최근에 지난 하루 경계(KST 05시)다.
        assertTrue(findTargets(createdBefore = Instant.now().minus(1, ChronoUnit.DAYS)).isEmpty())
    }

    @Test
    fun `하한 이전에 만들어진 대화방은 대상에서 빠진다`() {
        // 요약 저장이 배포되기 전에 만들어진 방들 — 요약이 없어 카드를 만들 수 없으므로,
        // 하한이 없으면 첫 실행이 이 방들을 전부 카드 없이 닫아버린다.
        saveWithSummary()

        assertTrue(findTargets(createdAfter = Instant.now().plus(1, ChronoUnit.HOURS)).isEmpty())
    }

    @Test
    fun `대상은 회원을 가리지 않고 모두 모은다`() {
        val mine = saveWithSummary(memberId = 1L)
        val others = saveWithSummary(memberId = 2L)

        assertEquals(listOf(mine, others), findTargets())
    }
}
