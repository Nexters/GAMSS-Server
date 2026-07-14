package com.nexters.gamss.global.security

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Date

/**
 * jjwt 기반 JwtIssuer 구현. 서명은 HMAC-SHA256.
 */
@Component
class JjwtIssuer(
    properties: JwtProperties,
) : JwtIssuer {
    private val key = Keys.hmacShaKeyFor(properties.secret.toByteArray(StandardCharsets.UTF_8))
    private val accessTokenValidity = properties.accessTokenValidity
    private val refreshTokenValidity = properties.refreshTokenValidity

    override fun issueAccessToken(memberId: Long): String = build(memberId, accessTokenValidity)

    override fun issueRefreshToken(memberId: Long): String = build(memberId, refreshTokenValidity)

    override fun parseMemberId(token: String): Long = parse(token).subject.toLong()

    private fun build(
        memberId: Long,
        validity: Duration,
    ): String {
        val now = Instant.now()
        return Jwts
            .builder()
            .subject(memberId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(validity)))
            .signWith(key)
            .compact()
    }

    private fun parse(token: String): Claims =
        try {
            Jwts
                .parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: ExpiredJwtException) {
            throw BusinessException(ErrorCode.EXPIRED_TOKEN)
        } catch (e: JwtException) {
            throw BusinessException(ErrorCode.INVALID_TOKEN)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(ErrorCode.INVALID_TOKEN)
        }
}
