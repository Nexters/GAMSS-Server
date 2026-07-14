package com.nexters.gamss.auth.oauth

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OidcTokenVerifierTest {
    private val issuer = "https://accounts.google.com"
    private val clientId = "gamss-client-id"
    private val rsaKey: RSAKey = RSAKeyGenerator(2048).keyID("test-key").generate()
    private val jwkSource = ImmutableJWKSet<SecurityContext>(JWKSet(rsaKey))
    private val provider =
        OAuthProperties.Provider(
            issuer = issuer,
            jwksUri = "https://unused",
            clientIds = listOf(clientId),
        )
    private val verifier = OidcTokenVerifier(provider, jwkSource)

    @Test
    fun `유효한 토큰이면 사용자 정보를 반환한다`() {
        val info = verifier.verify(signedToken())

        assertEquals("user-sub-1", info.providerId)
        assertEquals("user@example.com", info.email)
    }

    @Test
    fun `이메일 클레임이 없어도 검증에 성공한다`() {
        val info = verifier.verify(signedToken(email = null))

        assertNull(info.email)
    }

    @Test
    fun `발급자가 다르면 INVALID_SOCIAL_TOKEN`() {
        assertSocialTokenInvalid(signedToken(iss = "https://evil.example.com"))
    }

    @Test
    fun `대상(aud)이 허용 목록에 없으면 INVALID_SOCIAL_TOKEN`() {
        assertSocialTokenInvalid(signedToken(aud = "other-client"))
    }

    @Test
    fun `만료된 토큰이면 INVALID_SOCIAL_TOKEN`() {
        assertSocialTokenInvalid(signedToken(expiresAt = Date.from(Instant.now().minusSeconds(60))))
    }

    @Test
    fun `등록되지 않은 키로 서명하면 INVALID_SOCIAL_TOKEN`() {
        val anotherKey = RSAKeyGenerator(2048).keyID("another-key").generate()
        assertSocialTokenInvalid(signedToken(signingKey = anotherKey))
    }

    @Test
    fun `형식이 잘못된 토큰이면 INVALID_SOCIAL_TOKEN`() {
        assertSocialTokenInvalid("not-a-jwt")
    }

    @Test
    fun `subject(sub)가 없으면 INVALID_SOCIAL_TOKEN`() {
        assertSocialTokenInvalid(signedToken(subject = null))
    }

    private fun assertSocialTokenInvalid(token: String) {
        val exception = assertFailsWith<BusinessException> { verifier.verify(token) }
        assertEquals(ErrorCode.INVALID_SOCIAL_TOKEN, exception.errorCode)
    }

    private fun signedToken(
        subject: String? = "user-sub-1",
        iss: String = issuer,
        aud: String = clientId,
        email: String? = "user@example.com",
        expiresAt: Date = Date.from(Instant.now().plusSeconds(300)),
        signingKey: RSAKey = rsaKey,
    ): String {
        val builder =
            JWTClaimsSet
                .Builder()
                .issuer(iss)
                .audience(aud)
                .expirationTime(expiresAt)
                .issueTime(Date())
        if (subject != null) builder.subject(subject)
        if (email != null) builder.claim("email", email)
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.keyID).build(), builder.build())
        jwt.sign(RSASSASigner(signingKey))
        return jwt.serialize()
    }
}
