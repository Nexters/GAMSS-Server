package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 05:00 알림 대상 조회. **이번 실행에서 카드가 만들어진** 회원만 잡아야 한다. 여기가 어긋나면 카드가
 * 없는 사람에게 "카드가 도착했어요"가 가거나, 받은 사람이 소식을 못 듣는다.
 */
class CardCreatedMemberQueryTest : RepositoryTest() {
    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Test
    fun `기준 시각 이후에 카드가 만들어진 회원만 돌려준다`() {
        save(memberId = 1L, status = CardGenerationStatus.DONE, updatedAt = AFTER_SINCE)
        save(memberId = 2L, status = CardGenerationStatus.DONE, updatedAt = BEFORE_SINCE)

        assertEquals(listOf(1L), findTargets())
    }

    /** 카드가 안 만들어진 결과들이다. 이 사람들에게 "카드가 도착했어요"가 가면 안 된다. */
    @Test
    fun `DONE이 아닌 상태는 제외한다`() {
        save(memberId = 1L, status = CardGenerationStatus.SKIPPED, updatedAt = AFTER_SINCE)
        save(memberId = 2L, status = CardGenerationStatus.FAILED, updatedAt = AFTER_SINCE)
        save(memberId = 3L, status = CardGenerationStatus.PENDING, updatedAt = AFTER_SINCE)
        save(memberId = 4L, status = CardGenerationStatus.NONE, updatedAt = AFTER_SINCE)

        assertEquals(emptyList(), findTargets())
    }

    @Test
    fun `방이 여러 개여도 회원은 한 번만 나온다`() {
        repeat(3) { save(memberId = 1L, status = CardGenerationStatus.DONE, updatedAt = AFTER_SINCE) }

        assertEquals(listOf(1L), findTargets())
    }

    /** 경계는 포함이다. 배치가 05:00:00.000 에 만든 카드가 빠지면 안 된다. */
    @Test
    fun `기준 시각 정각도 포함한다`() {
        save(memberId = 1L, status = CardGenerationStatus.DONE, updatedAt = SINCE)

        assertEquals(listOf(1L), findTargets())
    }

    private fun findTargets(): List<Long> = conversationRepository.findMemberIdsWithCardCreatedSince(SINCE)

    private fun save(
        memberId: Long,
        status: CardGenerationStatus,
        updatedAt: Instant,
    ) {
        val conversation = conversationRepository.save(Conversation(memberId = memberId))
        conversationRepository.updateCardGenerationStatus(
            conversation.id,
            status,
            CardGenerationStatus.entries,
            updatedAt,
        )
    }

    companion object {
        private val SINCE: Instant = Instant.parse("2026-08-18T20:00:00Z")
        private val AFTER_SINCE: Instant = Instant.parse("2026-08-18T20:00:30Z")
        private val BEFORE_SINCE: Instant = Instant.parse("2026-08-18T19:59:00Z")
    }
}
