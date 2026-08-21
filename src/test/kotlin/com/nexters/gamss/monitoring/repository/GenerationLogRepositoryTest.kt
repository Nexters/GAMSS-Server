package com.nexters.gamss.monitoring.repository

import com.nexters.gamss.monitoring.domain.GenerationLog
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GenerationLogRepositoryTest : RepositoryTest() {
    @Autowired
    lateinit var generationLogRepository: GenerationLogRepository

    private fun log(
        type: GenerationType,
        usedTokens: Int,
        memberId: Long? = MEMBER_ID,
        createdAt: Instant = NOW,
    ): GenerationLog =
        GenerationLog(
            generationType = type,
            model = "gemini-3.1-flash-lite",
            memberId = memberId,
            success = true,
            attemptCount = 1,
            usedTokens = usedTokens,
            latencyMs = 100,
            createdAt = createdAt,
        )

    /**
     * 카드는 대화 1개당 1장이라 반복 소비가 구조적으로 불가능하다. 눌러서 계속 만들 수 있는
     * 댓글·답글과 같은 통을 쓰면, 카드 몇 장에 그날의 댓글 한도가 깎인다.
     */
    @Test
    fun `카드 생성 토큰은 일일 사용량 합에 들어가지 않는다`() {
        generationLogRepository.save(log(GenerationType.COMMENT, 100))
        generationLogRepository.save(log(GenerationType.REPLY, 200))
        generationLogRepository.save(log(GenerationType.CARD, 5_000))
        generationLogRepository.save(log(GenerationType.CARD_EMOTION, 3_000))

        val used = generationLogRepository.sumUsedTokensByMemberSince(MEMBER_ID, SINCE)

        assertEquals(300, used, "댓글 100 + 답글 200 만 세야 한다")
    }

    /** 카드만 쓴 회원은 한도를 전혀 쓰지 않은 것으로 보여야 한다. */
    @Test
    fun `카드만 만든 회원의 사용량은 0이다`() {
        generationLogRepository.save(log(GenerationType.CARD, 9_999))
        generationLogRepository.save(log(GenerationType.CARD_EMOTION, 9_999))

        assertEquals(0, generationLogRepository.sumUsedTokensByMemberSince(MEMBER_ID, SINCE))
    }

    /** 합산에서만 빼는 것이라 다른 회원·기간 조건은 그대로 지켜져야 한다. */
    @Test
    fun `다른 회원과 기간 밖의 로그는 그대로 제외된다`() {
        generationLogRepository.save(log(GenerationType.COMMENT, 100))
        generationLogRepository.save(log(GenerationType.COMMENT, 500, memberId = OTHER_MEMBER_ID))
        generationLogRepository.save(log(GenerationType.COMMENT, 700, createdAt = SINCE.minusSeconds(60)))

        assertEquals(100, generationLogRepository.sumUsedTokensByMemberSince(MEMBER_ID, SINCE))
    }

    private companion object {
        const val MEMBER_ID = 1L
        const val OTHER_MEMBER_ID = 2L
        val SINCE: Instant = Instant.parse("2026-08-21T00:00:00Z")
        val NOW: Instant = Instant.parse("2026-08-21T09:00:00Z")
    }
}
