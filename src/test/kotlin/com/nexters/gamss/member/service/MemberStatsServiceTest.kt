package com.nexters.gamss.member.service

import com.nexters.gamss.member.domain.MemberStatus
import com.nexters.gamss.member.repository.MemberRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberStatsServiceTest {
    private val memberRepository = mockk<MemberRepository>()
    private val memberStatsService = MemberStatsService(memberRepository)

    @Test
    fun `getStats는 상태별 수와 지정한 일수만큼 가입 추이를 채운다`() {
        every { memberRepository.count() } returns 10L
        every { memberRepository.countByStatus(MemberStatus.ACTIVE) } returns 7L
        every { memberRepository.countByStatus(MemberStatus.WITHDRAWN) } returns 3L
        every { memberRepository.findCreatedAtsSince(any()) } returns listOf(Instant.now(), Instant.now())

        val stats = memberStatsService.getStats(14)

        assertEquals(10L, stats.total)
        assertEquals(7L, stats.active)
        assertEquals(3L, stats.withdrawn)
        assertEquals(14, stats.dailySignups.size)
        // 자정 경계 플래키를 피하려고 특정 날짜가 아니라 기간 합계로 단언한다(오늘 가입 2명).
        assertEquals(2L, stats.dailySignups.sumOf { it.count })
    }
}
