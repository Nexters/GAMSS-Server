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

    override fun issueAccessToken(memberId: Long): String = build(memberId.toString(), TokenType.ACCESS, accessTokenValidity)

    override fun issueRefreshToken(memberId: Long): String = build(memberId.toString(), TokenType.REFRESH, refreshTokenValidity)

    // 관리자 토큰은 재발급이 없으므로 access 와 같은 유효기간을 쓰고, 만료 시 재로그인한다.
    override fun issueAdminToken(email: String): String = build(email, TokenType.ADMIN, accessTokenValidity)

    override fun parseAccessToken(token: String): Long = parseSubject(token, TokenType.ACCESS).toLong()

    override fun parseRefreshToken(token: String): Long = parseSubject(token, TokenType.REFRESH).toLong()

    override fun parseAdminToken(token: String): String = parseSubject(token, TokenType.ADMIN)

    private fun build(
        subject: String,
        type: TokenType,
        validity: Duration,
    ): String {
        val now = Instant.now()
        return Jwts
            .builder()
            .subject(subject)
            .claim(TYPE_CLAIM, type.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(validity)))
            .signWith(key)
            .compact()
    }

    private fun parseSubject(
        token: String,
        expected: TokenType,
    ): String {
        val claims = parse(token)
        // 종류 클레임이 없는 토큰(구버전)도 여기서 거부된다.
        if (TokenType.from(claims[TYPE_CLAIM] as? String) != expected) {
            throw BusinessException(ErrorCode.INVALID_TOKEN)
        }
        return claims.subject
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

    companion object {
        private const val TYPE_CLAIM = "type"
    }
}
