package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.repository.RefreshTokenRepository
import com.nexters.gamss.auth.repository.SocialAccountRepository
import com.nexters.gamss.auth.social.SocialProvider
import com.nexters.gamss.auth.social.SocialTokenVerifier
import com.nexters.gamss.auth.social.SocialUser
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.MemberStatus
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 탈퇴 → 재가입 흐름 검증. 탈퇴가 소셜 계정 연결을 끊지 않으면 같은 소셜 계정으로 다시 로그인해도
 * 탈퇴한 회원이 그대로 조회돼 재가입이 영영 막힌다(#72).
 *
 * 커밋된 상태를 봐야 하므로 클래스 레벨 트랜잭션 롤백을 쓰지 않고 직접 정리한다
 * ([ConcurrentLoginIntegrationTest]와 같은 이유).
 */
@SpringBootTest
@Import(TestcontainersConfig::class, WithdrawnMemberRejoinIntegrationTest.StubVerifierConfig::class)
class WithdrawnMemberRejoinIntegrationTest {
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

    @TestConfiguration(proxyBeanMethods = false)
    class StubVerifierConfig {
        // 같은 소셜 신원을 반환해, 재로그인이 '같은 계정으로 다시 들어오는' 상황이 되게 한다.
        @Bean
        @Primary
        fun stubSocialTokenVerifier(): SocialTokenVerifier =
            object : SocialTokenVerifier {
                override fun verify(idToken: String) = SocialUser("rejoin-uid", SocialProvider.GOOGLE, "rejoin@a.com", "재가입")
            }
    }

    @AfterEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        socialAccountRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `탈퇴한 소셜 계정으로 다시 로그인하면 신규 회원으로 재가입된다`() {
        val first = authService.login("idtok")
        val firstMemberId = memberRepository.findAll().single().id
        memberService.withdraw(firstMemberId)

        val second = authService.login("idtok")

        val members = memberRepository.findAll().sortedBy { it.id }
        assertEquals(2, members.size, "재가입은 신규 회원 행으로 생성돼야 한다")
        assertEquals(MemberStatus.WITHDRAWN, members.first().status)
        assertEquals(MemberStatus.ACTIVE, members.last().status)
        assertNotEquals(first.accessToken, second.accessToken)
        assertTrue(first.isFirstLogin, "최초 로그인은 신규 가입이다")
        assertTrue(second.isFirstLogin, "재가입도 신규 가입으로 응답해야 한다")
        assertEquals(1L, socialAccountRepository.count(), "소셜 계정은 새 회원 것 하나만 남아야 한다")
        assertEquals(members.last().id, socialAccountRepository.findAll().single().memberId)
    }

    @Test
    fun `재가입한 회원은 탈퇴 전 회원과 다른 memberId를 가진다`() {
        authService.login("idtok")
        val oldMemberId = memberRepository.findAll().single().id
        memberService.withdraw(oldMemberId)

        authService.login("idtok")

        val newMemberId = socialAccountRepository.findAll().single().memberId
        assertNotEquals(oldMemberId, newMemberId, "이전 회원의 대화·카드에 접근되면 안 된다")
    }

    @Test
    fun `탈퇴하면 개인 식별정보가 비워지고 통계용 행만 남는다`() {
        authService.login("idtok")
        val memberId = memberRepository.findAll().single().id

        memberService.withdraw(memberId)

        val withdrawn = memberRepository.findById(memberId).orElseThrow()
        assertNull(withdrawn.email, "이메일은 비워져야 한다")
        assertNull(withdrawn.name, "이름은 비워져야 한다")
        assertNull(withdrawn.nickname, "닉네임은 비워져야 한다")
        assertEquals(MemberStatus.WITHDRAWN, withdrawn.status)
        assertEquals(1L, memberRepository.countByStatus(MemberStatus.WITHDRAWN), "탈퇴자 통계는 유지돼야 한다")
    }

    @Test
    fun `탈퇴하면 리프레시 토큰이 폐기된다`() {
        val issued = authService.login("idtok")
        val memberId = memberRepository.findAll().single().id

        memberService.withdraw(memberId)

        assertEquals(0L, refreshTokenRepository.count(), "잔여 유효기간 동안 재발급되면 안 된다")
        val exception = assertFailsWith<BusinessException> { authService.reissue(issued.refreshToken) }
        assertEquals(ErrorCode.REFRESH_TOKEN_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `탈퇴 후 재가입한 회원도 다시 탈퇴할 수 있다`() {
        authService.login("idtok")
        memberService.withdraw(memberRepository.findAll().single().id)
        authService.login("idtok")
        val rejoinedId = socialAccountRepository.findAll().single().memberId

        memberService.withdraw(rejoinedId)

        assertEquals(2L, memberRepository.countByStatus(MemberStatus.WITHDRAWN))
        assertEquals(0L, socialAccountRepository.count())
    }

    @Test
    fun `이미 탈퇴한 회원을 다시 탈퇴시키면 ALREADY_WITHDRAWN`() {
        authService.login("idtok")
        val memberId = memberRepository.findAll().single().id
        memberService.withdraw(memberId)

        val exception = assertFailsWith<BusinessException> { memberService.withdraw(memberId) }

        assertEquals(ErrorCode.ALREADY_WITHDRAWN, exception.errorCode)
    }
}
