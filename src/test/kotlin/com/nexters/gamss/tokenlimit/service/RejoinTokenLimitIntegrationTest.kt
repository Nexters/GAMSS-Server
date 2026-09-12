package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.auth.repository.RefreshTokenRepository
import com.nexters.gamss.auth.repository.SocialAccountRepository
import com.nexters.gamss.auth.service.AuthService
import com.nexters.gamss.auth.social.SocialProvider
import com.nexters.gamss.auth.social.SocialTokenVerifier
import com.nexters.gamss.auth.social.SocialUser
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * 이 이슈(#222)의 완료 조건을 직접 확인한다.
 *
 * 상한을 실제로 켠다. 켜지 않으면 [DailyTokenLimitService.isWithinLimit] 이 항상 true 라 아래
 * 테스트가 통과해도 아무것도 증명하지 못한다. 커밋된 상태를 봐야 하므로 클래스 레벨 트랜잭션
 * 롤백을 쓰지 않고 직접 정리한다([com.nexters.gamss.auth.service.WithdrawnMemberRejoinIntegrationTest]
 * 와 같은 이유).
 */
@SpringBootTest
@Import(TestcontainersConfig::class, RejoinTokenLimitIntegrationTest.StubVerifierConfig::class)
@TestPropertySource(properties = ["gamss.token-limit.enabled=true"])
class RejoinTokenLimitIntegrationTest {
    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var memberService: MemberService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var socialAccountRepository: SocialAccountRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var identityRepository: MemberSocialIdentityRepository

    @Autowired
    private lateinit var usageRepository: TokenQuotaUsageRepository

    @Autowired
    private lateinit var recorder: TokenQuotaRecorder

    @Autowired
    private lateinit var limitService: DailyTokenLimitService

    @Autowired
    private lateinit var tokenPolicyService: TokenPolicyService

    @TestConfiguration(proxyBeanMethods = false)
    class StubVerifierConfig {
        /** 같은 소셜 신원을 돌려줘, 재로그인이 '같은 사람이 다시 들어오는' 상황이 되게 한다. */
        @Bean
        @Primary
        fun stubSocialTokenVerifier(): SocialTokenVerifier =
            object : SocialTokenVerifier {
                override fun verify(idToken: String) = SocialUser("quota-uid", SocialProvider.GOOGLE, "quota@a.com", "쿼터")
            }
    }

    @AfterEach
    fun cleanUp() {
        usageRepository.deleteAll()
        identityRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        socialAccountRepository.deleteAll()
        memberRepository.deleteAll()
    }

    /** 이 이슈의 본론. */
    @Test
    fun `탈퇴 후 재가입해도 당일 사용량이 리셋되지 않는다`() {
        val exhausted = tokenPolicyService.current().dailyTokenLimit.toInt()
        authService.login("idtok")
        val oldMemberId = currentMemberId()
        recorder.record(oldMemberId, exhausted)
        assertFalse(limitService.isWithinLimit(oldMemberId), "전제가 깨졌다 - 한도를 넘긴 상태를 만들지 못했다")

        memberService.withdraw(oldMemberId)
        authService.login("idtok")

        val newMemberId = currentMemberId()
        assertNotEquals(oldMemberId, newMemberId, "전제가 깨졌다 - 재가입은 새 회원이어야 한다")
        assertEquals(exhausted.toLong(), limitService.usageFor(newMemberId).usedTokens, "재가입 회원이 이전 사용량을 이어받아야 한다")
        assertFalse(limitService.isWithinLimit(newMemberId), "탈퇴·재가입으로 한도가 리셋되면 안 된다")
    }

    @Test
    fun `재가입 후 사용량은 이전 사용량 위에 쌓인다`() {
        authService.login("idtok")
        val oldMemberId = currentMemberId()
        recorder.record(oldMemberId, 1_000)

        memberService.withdraw(oldMemberId)
        authService.login("idtok")
        val newMemberId = currentMemberId()
        recorder.record(newMemberId, 500)

        assertEquals(1_500, limitService.usageFor(newMemberId).usedTokens)
    }

    /** 사용량을 잇기 위해 기존 격리 계약을 깨뜨리지 않았다는 증거다(DoD). */
    @Test
    fun `사용량을 이어받아도 재가입은 여전히 새 회원이다`() {
        authService.login("idtok")
        val oldMemberId = currentMemberId()
        recorder.record(oldMemberId, 1_000)

        memberService.withdraw(oldMemberId)
        authService.login("idtok")

        val members = memberRepository.findAll().sortedBy { it.id }
        assertEquals(2, members.size, "재가입은 신규 회원 행으로 생성돼야 한다")
        assertNotEquals(oldMemberId, currentMemberId(), "이전 회원의 대화·카드에 접근되면 안 된다")
        assertEquals(1L, socialAccountRepository.count(), "소셜 계정은 새 회원 것 하나만 남아야 한다")
    }

    /** 끊겨도 위 테스트들이 통과하는 것이 핵심이다 - 이월은 주체 키의 결정론성이 떠받친다. */
    @Test
    fun `탈퇴하면 주체 매핑이 끊긴다`() {
        authService.login("idtok")
        val memberId = currentMemberId()

        memberService.withdraw(memberId)

        assertFalse(identityRepository.existsByMemberId(memberId), "탈퇴자를 과거 행적에 연결할 고리를 남기지 않는다")
    }

    private fun currentMemberId(): Long = socialAccountRepository.findAll().single().memberId
}
