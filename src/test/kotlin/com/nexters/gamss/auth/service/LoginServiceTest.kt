package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.domain.RefreshToken
import com.nexters.gamss.auth.repository.RefreshTokenRepository
import com.nexters.gamss.auth.social.SocialProvider
import com.nexters.gamss.auth.social.SocialTokenVerifier
import com.nexters.gamss.auth.social.SocialUser
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.global.security.Sha256TokenHasher
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.service.MemberService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoginServiceTest {
    private val socialTokenVerifier = mockk<SocialTokenVerifier>()
    private val socialAccountService = mockk<SocialAccountService>()
    private val memberService = mockk<MemberService>()
    private val jwtIssuer = mockk<JwtIssuer>()
    private val refreshTokenRepository = mockk<RefreshTokenRepository>()

    // 실제 해시로 저장·비교되는지 검증하기 위해 진짜 해셔를 쓴다.
    private val tokenHasher = Sha256TokenHasher()
    private val loginService =
        LoginService(
            socialTokenVerifier,
            socialAccountService,
            memberService,
            jwtIssuer,
            refreshTokenRepository,
            tokenHasher,
        )

    @Test
    fun `로그인 시 신규 회원이면 리프레시 토큰을 저장한다`() {
        every { socialTokenVerifier.verify("idtok") } returns SocialUser("uid-1", SocialProvider.GOOGLE, "a@a.com", "홍길동")
        val member =
            mockk<Member> {
                every { id } returns 100L
                every { isWithdrawn() } returns false
            }
        every { socialAccountService.resolveMember(SocialProvider.GOOGLE, "uid-1", "a@a.com", "홍길동") } returns member
        every { jwtIssuer.issueAccessToken(100L) } returns "access"
        every { jwtIssuer.issueRefreshToken(100L) } returns "refresh"
        every { refreshTokenRepository.findByMemberId(100L) } returns null
        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        val result = loginService.login("idtok")

        assertEquals("access", result.accessToken)
        // 클라이언트에는 원본 토큰을, DB에는 해시를 저장한다.
        assertEquals("refresh", result.refreshToken)
        verify {
            refreshTokenRepository.save(
                match { it.memberId == 100L && it.token == tokenHasher.hash("refresh") },
            )
        }
    }

    @Test
    fun `로그인 시 기존 리프레시 토큰이 있으면 회전한다`() {
        every { socialTokenVerifier.verify("t") } returns SocialUser("uid", SocialProvider.APPLE, "e@e.com", "홍길동")
        val member =
            mockk<Member> {
                every { id } returns 1L
                every { isWithdrawn() } returns false
            }
        every { socialAccountService.resolveMember(SocialProvider.APPLE, "uid", "e@e.com", "홍길동") } returns member
        every { jwtIssuer.issueAccessToken(1L) } returns "a"
        every { jwtIssuer.issueRefreshToken(1L) } returns "r"
        val stored = mockk<RefreshToken>(relaxed = true)
        every { refreshTokenRepository.findByMemberId(1L) } returns stored

        loginService.login("t")

        verify { stored.rotate(tokenHasher.hash("r")) }
        verify(exactly = 0) { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `탈퇴한 회원이 로그인하면 WITHDRAWN_MEMBER`() {
        every { socialTokenVerifier.verify("idtok") } returns SocialUser("uid-1", SocialProvider.GOOGLE, "a@a.com", "홍길동")
        val member = mockk<Member> { every { isWithdrawn() } returns true }
        every { socialAccountService.resolveMember(SocialProvider.GOOGLE, "uid-1", "a@a.com", "홍길동") } returns member

        val exception = assertFailsWith<BusinessException> { loginService.login("idtok") }

        assertEquals(ErrorCode.WITHDRAWN_MEMBER, exception.errorCode)
    }

    @Test
    fun `재발급이 정상이면 새 토큰을 발급하고 회전한다`() {
        every { jwtIssuer.parseRefreshToken("refresh") } returns 100L
        val stored = mockk<RefreshToken>(relaxed = true)
        every { stored.matches(tokenHasher.hash("refresh")) } returns true
        every { refreshTokenRepository.findByMemberId(100L) } returns stored
        every { memberService.getById(100L) } returns mockk { every { isWithdrawn() } returns false }
        every { jwtIssuer.issueAccessToken(100L) } returns "na"
        every { jwtIssuer.issueRefreshToken(100L) } returns "nr"

        val result = loginService.reissue("refresh")

        assertEquals("na", result.accessToken)
        assertEquals("nr", result.refreshToken)
        verify { stored.rotate(tokenHasher.hash("nr")) }
    }

    @Test
    fun `탈퇴한 회원이 재발급하면 WITHDRAWN_MEMBER`() {
        every { jwtIssuer.parseRefreshToken("refresh") } returns 100L
        val stored = mockk<RefreshToken>()
        every { stored.matches(tokenHasher.hash("refresh")) } returns true
        every { refreshTokenRepository.findByMemberId(100L) } returns stored
        every { memberService.getById(100L) } returns mockk { every { isWithdrawn() } returns true }

        val exception = assertFailsWith<BusinessException> { loginService.reissue("refresh") }

        assertEquals(ErrorCode.WITHDRAWN_MEMBER, exception.errorCode)
    }

    @Test
    fun `재발급 시 저장된 토큰이 없으면 REFRESH_TOKEN_NOT_FOUND`() {
        every { jwtIssuer.parseRefreshToken("r") } returns 1L
        every { refreshTokenRepository.findByMemberId(1L) } returns null

        val exception = assertFailsWith<BusinessException> { loginService.reissue("r") }

        assertEquals(ErrorCode.REFRESH_TOKEN_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `재발급 시 토큰이 일치하지 않으면 INVALID_TOKEN`() {
        every { jwtIssuer.parseRefreshToken("r") } returns 1L
        val stored = mockk<RefreshToken>()
        every { stored.matches(tokenHasher.hash("r")) } returns false
        every { refreshTokenRepository.findByMemberId(1L) } returns stored

        val exception = assertFailsWith<BusinessException> { loginService.reissue("r") }

        assertEquals(ErrorCode.INVALID_TOKEN, exception.errorCode)
    }

    @Test
    fun `재발급 시 토큰 파싱 예외를 전파한다`() {
        every { jwtIssuer.parseRefreshToken("bad") } throws BusinessException(ErrorCode.EXPIRED_TOKEN)

        val exception = assertFailsWith<BusinessException> { loginService.reissue("bad") }

        assertEquals(ErrorCode.EXPIRED_TOKEN, exception.errorCode)
    }
}
