package com.nexters.gamss.global.security

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JjwtIssuerTest {
    private val properties =
        JwtProperties(
            secret = "test-secret-value-sufficiently-long-for-hs256-signing-0123456789abcdef",
            accessTokenValidity = Duration.ofHours(1),
            refreshTokenValidity = Duration.ofDays(14),
        )
    private val jwtIssuer = JjwtIssuer(properties)

    @Test
    fun `액세스 토큰을 발급하고 memberId를 파싱한다`() {
        val token = jwtIssuer.issueAccessToken(42L)

        assertEquals(42L, jwtIssuer.parseMemberId(token))
    }

    @Test
    fun `리프레시 토큰도 memberId를 파싱한다`() {
        val token = jwtIssuer.issueRefreshToken(7L)

        assertEquals(7L, jwtIssuer.parseMemberId(token))
    }

    @Test
    fun `서명이 다른 토큰은 INVALID_TOKEN 예외를 던진다`() {
        val other = JjwtIssuer(properties.copy(secret = "another-secret-value-also-long-enough-for-hs256-abcdefghijklmnop"))
        val forged = other.issueAccessToken(1L)

        val exception = assertFailsWith<BusinessException> { jwtIssuer.parseMemberId(forged) }

        assertEquals(ErrorCode.INVALID_TOKEN, exception.errorCode)
    }

    @Test
    fun `만료된 토큰은 EXPIRED_TOKEN 예외를 던진다`() {
        val shortLived = JjwtIssuer(properties.copy(accessTokenValidity = Duration.ofMillis(1)))
        val token = shortLived.issueAccessToken(1L)
        Thread.sleep(50)

        val exception = assertFailsWith<BusinessException> { shortLived.parseMemberId(token) }

        assertEquals(ErrorCode.EXPIRED_TOKEN, exception.errorCode)
    }

    @Test
    fun `형식이 잘못된 토큰은 INVALID_TOKEN 예외를 던진다`() {
        val exception = assertFailsWith<BusinessException> { jwtIssuer.parseMemberId("not-a-valid-jwt") }

        assertEquals(ErrorCode.INVALID_TOKEN, exception.errorCode)
    }
}
