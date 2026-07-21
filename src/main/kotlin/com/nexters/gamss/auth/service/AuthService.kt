package com.nexters.gamss.auth.service

import org.springframework.stereotype.Service

/**
 * 인증 진입점. 로그인·재발급 흐름을 조율한다.
 *
 * 트랜잭션 작업은 LoginService 에, 동시 가입 경합의 재시도는 ConflictRetry 에 위임한다.
 * 재시도를 별도 객체로 두는 것은 트랜잭션 경계 바깥에서 실행돼야 하기 때문이자(같은 빈 내부
 * 호출은 트랜잭션 프록시를 거치지 않는다), 재시도 정책과 흐름 조율을 분리하기 위함이다.
 */
@Service
class AuthService(
    private val loginService: LoginService,
    private val conflictRetry: ConflictRetry,
) {
    fun login(idToken: String): TokenResult = conflictRetry.execute { loginService.login(idToken) }

    fun reissue(refreshToken: String): TokenResult = loginService.reissue(refreshToken)
}
