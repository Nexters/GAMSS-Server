package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.domain.SocialAccount
import com.nexters.gamss.auth.repository.SocialAccountRepository
import com.nexters.gamss.auth.social.SocialProvider
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.member.service.MemberSocialIdentityService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SocialAccountServiceTest {
    private val socialAccountRepository = mockk<SocialAccountRepository>()
    private val memberService = mockk<MemberService>()
    private val memberSocialIdentityService = mockk<MemberSocialIdentityService>(relaxed = true)
    private val socialAccountService =
        SocialAccountService(socialAccountRepository, memberService, memberSocialIdentityService)

    @Test
    fun `기존 소셜 계정이면 연결된 회원을 반환한다`() {
        val socialAccount = SocialAccount(memberId = 5L, provider = "GOOGLE", providerId = "sub-1")
        every { socialAccountRepository.findByProviderAndProviderId("GOOGLE", "sub-1") } returns socialAccount
        val member = mockk<Member>()
        every { memberService.getById(5L) } returns member

        val result = socialAccountService.resolveMember(SocialProvider.GOOGLE, "sub-1", "a@a.com", "홍길동")

        assertSame(member, result.member)
        assertFalse(result.isNewMember)
        verify(exactly = 0) { memberService.create(any(), any()) }
    }

    @Test
    fun `소셜 계정이 없으면 회원을 생성하고 연결한다`() {
        every { socialAccountRepository.findByProviderAndProviderId("APPLE", "sub-2") } returns null
        val member = mockk<Member> { every { id } returns 9L }
        every { memberService.create("b@a.com", "홍길동") } returns member
        every { socialAccountRepository.save(any()) } answers { firstArg() }

        val result = socialAccountService.resolveMember(SocialProvider.APPLE, "sub-2", "b@a.com", "홍길동")

        assertSame(member, result.member)
        assertTrue(result.isNewMember)
        verify {
            socialAccountRepository.save(
                match { it.memberId == 9L && it.provider == "APPLE" && it.providerId == "sub-2" },
            )
        }
    }

    @Test
    fun `소셜 계정 저장이 유니크 제약에 걸리면 도메인 예외로 번역한다`() {
        every { socialAccountRepository.findByProviderAndProviderId("GOOGLE", "sub-3") } returns null
        every { memberService.create("c@a.com", null) } returns mockk { every { id } returns 3L }
        every { socialAccountRepository.save(any()) } throws DataIntegrityViolationException("duplicate")

        assertFailsWith<ConcurrentRegistrationException> {
            socialAccountService.resolveMember(SocialProvider.GOOGLE, "sub-3", "c@a.com", null)
        }
    }
}
