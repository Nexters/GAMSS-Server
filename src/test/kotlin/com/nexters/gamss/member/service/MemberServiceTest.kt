package com.nexters.gamss.member.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
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
}
