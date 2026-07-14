package com.nexters.gamss.global.security

/**
 * 서비스 자체 토큰(access/refresh) 발급·파싱 추상화. 구현(JWT 등)은 교체 가능하다.
 */
interface JwtIssuer {
    fun issueAccessToken(memberId: Long): String

    fun issueRefreshToken(memberId: Long): String

    fun parseMemberId(token: String): Long
}
