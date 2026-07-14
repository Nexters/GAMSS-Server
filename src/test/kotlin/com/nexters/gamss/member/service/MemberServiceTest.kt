package com.nexters.gamss.member.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.Nickname
import com.nexters.gamss.member.repository.MemberRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MemberServiceTest {
    private val memberRepository = mockk<MemberRepository>()
    private val memberService = MemberService(memberRepository)

    @Test
    fun `회원을 생성한다`() {
        every { memberRepository.save(any()) } answers { firstArg() }

        val member = memberService.create("a@example.com")

        assertEquals("a@example.com", member.email)
        verify(exactly = 1) { memberRepository.save(any()) }
    }

    @Test
    fun `getById로 회원을 조회한다`() {
        val member = Member("b@example.com")
        every { memberRepository.findById(10L) } returns Optional.of(member)

        assertSame(member, memberService.getById(10L))
    }

    @Test
    fun `없는 회원을 조회하면 MEMBER_NOT_FOUND`() {
        every { memberRepository.findById(99L) } returns Optional.empty()

        val exception = assertFailsWith<BusinessException> { memberService.getById(99L) }

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `닉네임을 수정한다`() {
        val member = Member("b@example.com")
        every { memberRepository.findById(10L) } returns Optional.of(member)

        val updated = memberService.updateNickname(10L, Nickname("바다"))

        assertSame(member, updated)
        assertEquals(Nickname("바다"), updated.nickname)
    }

    @Test
    fun `회원을 탈퇴 처리한다`() {
        val member = Member("b@example.com")
        every { memberRepository.findById(10L) } returns Optional.of(member)

        memberService.withdraw(10L)

        assertEquals(true, member.isWithdrawn())
    }

    @Test
    fun `이미 탈퇴한 회원을 다시 탈퇴하면 ALREADY_WITHDRAWN`() {
        val member = Member("b@example.com").apply { withdraw() }
        every { memberRepository.findById(10L) } returns Optional.of(member)

        val exception = assertFailsWith<BusinessException> { memberService.withdraw(10L) }

        assertEquals(ErrorCode.ALREADY_WITHDRAWN, exception.errorCode)
    }
}
