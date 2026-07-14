package com.nexters.gamss.member.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.OAuthProvider
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
    fun `기존 회원이면 저장하지 않고 반환한다`() {
        val member = Member(OAuthProvider.GOOGLE, "sub-1", "a@example.com")
        every { memberRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "sub-1") } returns member

        val result = memberService.findOrCreate(OAuthProvider.GOOGLE, "sub-1", "a@example.com")

        assertSame(member, result)
        verify(exactly = 0) { memberRepository.save(any()) }
    }

    @Test
    fun `신규 회원이면 저장한다`() {
        every { memberRepository.findByProviderAndProviderId(OAuthProvider.APPLE, "sub-2") } returns null
        every { memberRepository.save(any()) } answers { firstArg() }

        val result = memberService.findOrCreate(OAuthProvider.APPLE, "sub-2", "b@example.com")

        assertEquals("sub-2", result.providerId)
        assertEquals(OAuthProvider.APPLE, result.provider)
        verify(exactly = 1) { memberRepository.save(any()) }
    }

    @Test
    fun `기존 회원의 이메일이 바뀌면 갱신한다`() {
        val member = Member(OAuthProvider.GOOGLE, "sub-3", "old@example.com")
        every { memberRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "sub-3") } returns member

        memberService.findOrCreate(OAuthProvider.GOOGLE, "sub-3", "new@example.com")

        assertEquals("new@example.com", member.email)
    }

    @Test
    fun `getById로 회원을 조회한다`() {
        val member = Member(OAuthProvider.GOOGLE, "sub-4", "c@example.com")
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
