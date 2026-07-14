package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.domain.RefreshToken
import com.nexters.gamss.auth.oauth.OAuthClientResolver
import com.nexters.gamss.auth.oauth.OAuthProvider
import com.nexters.gamss.auth.repository.RefreshTokenRepository
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.global.security.JwtIssuer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 소셜 로그인·토큰 재발급 오케스트레이션.
 * 소셜 검증(OAuthClient), 소셜↔회원 연결(SocialAccountService), 토큰(JwtIssuer)은 각 컴포넌트에 위임한다.
 */
@Service
class AuthService(
    private val oAuthClientResolver: OAuthClientResolver,
    private val socialAccountService: SocialAccountService,
    private val jwtIssuer: JwtIssuer,
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    @Transactional
    fun login(
        provider: OAuthProvider,
        idToken: String,
    ): TokenResult {
        val userInfo = oAuthClientResolver.resolve(provider).verify(idToken)
        val member = socialAccountService.resolveMember(provider, userInfo.providerId, userInfo.email)
        return issueTokens(member.id)
    }

    @Transactional
    fun reissue(refreshToken: String): TokenResult {
        val memberId = jwtIssuer.parseMemberId(refreshToken)
        val stored =
            refreshTokenRepository.findByMemberId(memberId)
                ?: throw BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)
        if (!stored.matches(refreshToken)) {
            throw BusinessException(ErrorCode.INVALID_TOKEN)
        }
        return issueTokens(memberId)
    }

    private fun issueTokens(memberId: Long): TokenResult {
        val accessToken = jwtIssuer.issueAccessToken(memberId)
        val refreshToken = jwtIssuer.issueRefreshToken(memberId)
        persistRefreshToken(memberId, refreshToken)
        return TokenResult(accessToken, refreshToken)
    }

    private fun persistRefreshToken(
        memberId: Long,
        refreshToken: String,
    ) {
        val stored = refreshTokenRepository.findByMemberId(memberId)
        if (stored == null) {
            refreshTokenRepository.save(RefreshToken(memberId, refreshToken))
            return
        }
        stored.rotate(refreshToken)
    }
}
