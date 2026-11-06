package com.nexters.gamss.payment.service

import com.nexters.gamss.auth.repository.RefreshTokenRepository
import com.nexters.gamss.auth.repository.SocialAccountRepository
import com.nexters.gamss.auth.service.AuthService
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.payment.repository.PaymentAccountRepository
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.tokenlimit.service.RejoinTokenLimitIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** 같은 소셜 신원을 돌려주는 스텁과 설정을 [RejoinTokenLimitIntegrationTest] 와 맞춰 컨텍스트를 함께 쓴다. */
@SpringBootTest
@Import(TestcontainersConfig::class, RejoinTokenLimitIntegrationTest.StubVerifierConfig::class)
@TestPropertySource(properties = ["gamss.token-limit.enabled=true"])
class RejoinPaymentAccountIntegrationTest {
    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var memberService: MemberService

    @Autowired
    private lateinit var accountService: PaymentAccountService

    @Autowired
    private lateinit var accountRepository: PaymentAccountRepository

    @Autowired
    private lateinit var identityRepository: MemberSocialIdentityRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var socialAccountRepository: SocialAccountRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @AfterEach
    fun cleanUp() {
        accountRepository.deleteAll()
        identityRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        socialAccountRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `탈퇴 후 재가입해도 같은 계정 토큰을 받는다`() {
        authService.login("idtok")
        val oldMemberId = currentMemberId()
        val before = accountService.accountTokenOf(oldMemberId)

        memberService.withdraw(oldMemberId)
        authService.login("idtok")

        val newMemberId = currentMemberId()
        assertNotEquals(oldMemberId, newMemberId, "전제가 깨졌다: 재가입은 새 회원이어야 한다")
        assertEquals(before, accountService.accountTokenOf(newMemberId))
        assertEquals(1, accountRepository.count(), "탈퇴해도 계정 토큰 바인딩은 남아야 한다")
    }

    private fun currentMemberId(): Long = socialAccountRepository.findAll().single().memberId
}
