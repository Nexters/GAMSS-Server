package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.oauth.OAuthProvider
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

/**
 * 소셜 로그인 진입점. 동시 최초 로그인 경합을 트랜잭션 경계 바깥에서 재시도한다.
 *
 * 최초 로그인 요청이 동시에 들어오면 두 요청 모두 소셜 계정을 없다고 보고 회원을 생성해,
 * 뒤늦은 하나가 (provider, providerId) 유니크 제약에 걸린다. 이때 login 트랜잭션은
 * rollback-only가 되어 같은 트랜잭션 안에서는 회복할 수 없다 —
 * REQUIRES_NEW로 재조회를 분리해도 바깥 트랜잭션이 이미 오염돼 커밋이 실패한다.
 *
 * 그래서 트랜잭션 경계 바깥인 이 빈에서 한 번만 재시도한다. login 은 소셜 계정과
 * 리프레시 토큰을 한 트랜잭션에서 커밋하므로, 재시도 시점에는 앞선 요청이 커밋한 둘 다
 * 조회되어(소셜 계정 → 기존 회원, 리프레시 토큰 → 회전) 유니크 충돌 없이 성공한다.
 *
 * AuthService 를 별도 빈으로 두는 이유도 이것이다. 같은 빈 안에서 재시도하면
 * 자기 호출이라 트랜잭션 프록시를 거치지 않아 매 시도가 새 트랜잭션으로 시작되지 않는다.
 */
@Service
class AuthFacade(
    private val authService: AuthService,
) {
    fun login(
        provider: OAuthProvider,
        idToken: String,
    ): TokenResult =
        try {
            authService.login(provider, idToken)
        } catch (e: DataIntegrityViolationException) {
            authService.login(provider, idToken)
        }

    fun reissue(refreshToken: String): TokenResult = authService.reissue(refreshToken)
}
