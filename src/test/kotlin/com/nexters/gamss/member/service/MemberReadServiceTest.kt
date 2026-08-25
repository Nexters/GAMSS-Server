package com.nexters.gamss.member.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.MemberStatus
import com.nexters.gamss.member.repository.MemberRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MemberReadServiceTest {
    private val memberRepository = mockk<MemberRepository>()
    private val memberReadService = MemberReadService(memberRepository)

    @Test
    fun `getById로 회원을 조회한다`() {
        val member = Member("b@example.com")
        every { memberRepository.findById(10L) } returns Optional.of(member)

        assertSame(member, memberReadService.getById(10L))
    }

    @Test
    fun `없는 회원을 조회하면 MEMBER_NOT_FOUND`() {
        every { memberRepository.findById(99L) } returns Optional.empty()

        val exception = assertFailsWith<BusinessException> { memberReadService.getById(99L) }

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `getStats는 상태별 수와 지정한 일수만큼 가입 추이를 채운다`() {
        every { memberRepository.count() } returns 10L
        every { memberRepository.countByStatus(MemberStatus.ACTIVE) } returns 7L
        every { memberRepository.countByStatus(MemberStatus.WITHDRAWN) } returns 3L
        every { memberRepository.findCreatedAtsSince(any()) } returns listOf(Instant.now(), Instant.now())

        val stats = memberReadService.getStats(14)

        assertEquals(10L, stats.total)
        assertEquals(7L, stats.active)
        assertEquals(3L, stats.withdrawn)
        assertEquals(14, stats.dailySignups.size)
        // 자정 경계 플래키를 피하려고 특정 날짜가 아니라 기간 합계로 단언한다(오늘 가입 2명).
        assertEquals(2L, stats.dailySignups.sumOf { it.count })
    }
}
