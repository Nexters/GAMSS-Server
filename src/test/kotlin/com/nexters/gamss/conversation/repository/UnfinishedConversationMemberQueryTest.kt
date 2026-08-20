package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.support.RepositoryTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 리마인더 대상 조회. **아직 미종료인 방**만, 그리고 5시 배치와 같은 기간 안의 것만 잡아야 한다.
 * 여기가 어긋나면 이미 마무리한 사람에게 "마무리하세요"가 가거나, 닫히지도 않을 방을 두고 알린다.
 */
class UnfinishedConversationMemberQueryTest : RepositoryTest() {
    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Test
    fun `미종료 방의 주인만 돌려준다`() {
        val unfinished = save(memberId = 1L, createdAt = INSIDE)
        val ended = save(memberId = 2L, createdAt = INSIDE).also { it.end() }
        val deleted = save(memberId = 3L, createdAt = INSIDE).also { it.delete() }
        conversationRepository.saveAll(listOf(ended, deleted))

        val memberIds = findTargets()

        assertEquals(listOf(unfinished.memberId), memberIds)
    }

    @Test
    fun `방이 여러 개여도 회원은 한 번만 나온다`() {
        save(memberId = 1L, createdAt = INSIDE)
        save(memberId = 1L, createdAt = INSIDE)
        save(memberId = 1L, createdAt = INSIDE)

        assertEquals(listOf(1L), findTargets())
    }

    @Test
    fun `기간 밖의 방은 제외한다`() {
        save(memberId = 1L, createdAt = CREATED_AFTER.minusSeconds(1))
        save(memberId = 2L, createdAt = CREATED_BEFORE)

        assertEquals(emptyList(), findTargets())
    }

    /** 하한은 포함, 상한은 제외다. 경계를 반대로 잡으면 하루가 밀리거나 겹친다. */
    @Test
    fun `경계는 하한 포함 상한 제외다`() {
        save(memberId = 1L, createdAt = CREATED_AFTER)
        save(memberId = 2L, createdAt = CREATED_BEFORE.minusSeconds(1))

        assertEquals(listOf(1L, 2L), findTargets().sorted())
    }

    private fun findTargets(): List<Long> = conversationRepository.findMemberIdsWithUnfinishedConversations(CREATED_AFTER, CREATED_BEFORE)

    private fun save(
        memberId: Long,
        createdAt: Instant,
    ): Conversation {
        val conversation = conversationRepository.save(Conversation(memberId = memberId))
        // createdAt 은 감사 필드라 저장 시점 값이 들어간다. 기간 조건을 보려면 직접 되돌린다
        // (ConversationRepositoryPastSummaryTest 와 같은 방식).
        entityManager
            .createQuery("update Conversation c set c.createdAt = :createdAt where c.id = :id")
            .setParameter("createdAt", createdAt)
            .setParameter("id", conversation.id)
            .executeUpdate()
        entityManager.clear()
        return conversation
    }

    companion object {
        private val CREATED_AFTER: Instant = Instant.parse("2026-08-14T20:00:00Z")
        private val CREATED_BEFORE: Instant = Instant.parse("2026-08-17T20:00:00Z")
        private val INSIDE: Instant = Instant.parse("2026-08-16T00:00:00Z")
    }
}
