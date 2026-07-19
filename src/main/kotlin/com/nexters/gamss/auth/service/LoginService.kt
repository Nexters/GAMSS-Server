package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.domain.RefreshToken
import com.nexters.gamss.auth.repository.RefreshTokenRepository
import com.nexters.gamss.auth.social.SocialTokenVerifier
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.global.security.TokenHasher
import com.nexters.gamss.member.service.MemberService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 로그인·재발급의 트랜잭션 작업 단위. 소셜 검증·회원 확보·토큰 발급을 한 트랜잭션으로 묶는다.
 *
 * 동시 최초 로그인 경합의 재시도는 이 트랜잭션 바깥(AuthService + ConflictRetry)에서 담당한다.
 * 소셜 계정과 리프레시 토큰을 한 트랜잭션으로 커밋하므로, 경합에 진 요청은 소셜 계정 생성에서
 * 걸려 통째로 롤백된다 — 토큰 발급까지 가지 않아 충돌 지점이 소셜 계정 하나로 한정된다.
 */
@Service
class LoginService(
    private val socialTokenVerifier: SocialTokenVerifier,
    private val socialAccountService: SocialAccountService,
    private val memberService: MemberService,
    private val jwtIssuer: JwtIssuer,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val tokenHasher: TokenHasher,
) {
    @Transactional
    fun login(idToken: String): TokenResult {
        val user = socialTokenVerifier.verify(idToken)
        val member = socialAccountService.resolveMember(user.provider, user.uid, user.email)
        if (member.isWithdrawn()) {
            throw BusinessException(ErrorCode.WITHDRAWN_MEMBER)
        }
        return issueTokens(member.id)
    }

    @Transactional
    fun reissue(refreshToken: String): TokenResult {
        val memberId = jwtIssuer.parseRefreshToken(refreshToken)
        val stored =
            refreshTokenRepository.findByMemberId(memberId)
                ?: throw BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)
        if (!stored.matches(tokenHasher.hash(refreshToken))) {
            throw BusinessException(ErrorCode.INVALID_TOKEN)
        }
        if (memberService.getById(memberId).isWithdrawn()) {
            throw BusinessException(ErrorCode.WITHDRAWN_MEMBER)
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
        // 원본 토큰은 클라이언트에만 주고, DB에는 해시만 저장한다(유출 시 재사용 방지).
        val hashed = tokenHasher.hash(refreshToken)
        val stored = refreshTokenRepository.findByMemberId(memberId)
        if (stored == null) {
            refreshTokenRepository.save(RefreshToken(memberId, hashed))
            return
        }
        stored.rotate(hashed)
    }
}
