package com.nexters.gamss.global.security

/**
 * 서비스 자체 토큰(access/refresh) 발급·파싱 추상화. 구현(JWT 등)은 교체 가능하다.
 *
 * 발급과 파싱을 종류별로 대칭되게 둔다. 파싱을 하나로 합치면 호출부가 토큰 종류를
 * 확인할 방법이 없어져, refresh 토큰으로 인증이 통과하는 것을 막지 못한다.
 */
interface JwtIssuer {
    fun issueAccessToken(memberId: Long): String

    fun issueRefreshToken(memberId: Long): String

    /** access 토큰이 아니면 INVALID_TOKEN 으로 거부한다. */
    fun parseAccessToken(token: String): Long

    /** refresh 토큰이 아니면 INVALID_TOKEN 으로 거부한다. */
    fun parseRefreshToken(token: String): Long
}
