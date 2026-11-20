package com.nexters.gamss.payment.google

import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.json.webtoken.JsonWebSignature
import com.google.api.client.json.webtoken.JsonWebToken
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.auth.http.HttpTransportFactory
import com.nexters.gamss.payment.google.OidcGooglePushAuthenticator.Companion.toLogSafe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.io.IOException
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(OutputCaptureExtension::class)
class OidcGooglePushAuthenticatorTest {
    private val keyPair = keyPair("RSA", 2048)
    private val otherKeyPair = keyPair("RSA", 2048)
    private val properties = GoogleRtdnProperties(audience = AUDIENCE, serviceAccountEmail = PUSH_ACCOUNT)

    /** audience 는 클래스가 설정값으로 넣는다. 테스트는 공개키만 바꾼다. */
    private val authenticator = OidcGooglePushAuthenticator(properties) { it.setPublicKey(keyPair.public) }

    @Test
    fun `Google 이 서명한 토큰이고 audience·서비스 계정·발급자가 맞으면 통과한다`() {
        assertEquals(GooglePushAuthResult.AUTHENTICATED, authenticator.authenticate(bearer(token())))
        assertEquals(GooglePushAuthResult.AUTHENTICATED, authenticator.authenticate(bearer(token(issuer = "accounts.google.com"))))
    }

    @Test
    fun `헤더가 없거나 Bearer 가 아니면 MISSING 이고 WARN 을 남긴다`(output: CapturedOutput) {
        assertEquals(GooglePushAuthResult.MISSING, authenticator.authenticate(null))
        assertEquals(GooglePushAuthResult.MISSING, authenticator.authenticate("Basic abc"))
        assertTrue(output.out.lines().any { "WARN" in it && "Bearer 토큰이 없습니다" in it })
    }

    @Test
    fun `Bearer 뒤가 비어 있으면 MISSING 이다`() {
        assertEquals(GooglePushAuthResult.MISSING, authenticator.authenticate("Bearer "))
        assertEquals(GooglePushAuthResult.MISSING, authenticator.authenticate("Bearer    "))
    }

    @Test
    fun `audience·서비스 계정·발급자·이메일 확인 여부가 다르면 거부한다`() {
        listOf(
            token(audience = "https://other.example.com"),
            token(email = "someone@evil.iam.gserviceaccount.com"),
            token(issuer = "https://evil.example.com"),
            token(emailVerified = false),
            token(expiresAt = Instant.now().minusSeconds(3600)),
        ).forEach { assertEquals(GooglePushAuthResult.REJECTED, authenticator.authenticate(bearer(it))) }
    }

    /** audience 를 설정값에서 읽지 않으면(운영 생성자에서 audience 설정이 빠지면) 이 테스트가 실패한다. */
    @Test
    fun `audience 는 설정값과 비교한다`() {
        val otherAudience =
            OidcGooglePushAuthenticator(GoogleRtdnProperties("https://other.example.com/api/webhooks/google", PUSH_ACCOUNT)) {
                it.setPublicKey(keyPair.public)
            }

        assertEquals(GooglePushAuthResult.REJECTED, otherAudience.authenticate(bearer(token())))
    }

    @Test
    fun `다른 키로 서명한 토큰은 거부한다`() {
        assertEquals(GooglePushAuthResult.REJECTED, authenticator.authenticate(bearer(token(signer = otherKeyPair.private))))
    }

    /** 라이브러리는 형식 오류를 감싸지 않고 IllegalArgumentException 등으로 던진다. 잡지 않으면 익명 요청이 500 이 된다. */
    @Test
    fun `형식이 깨진 토큰은 예외 없이 거부한다`() {
        val now = Instant.now().epochSecond
        val validPayload = """{"aud":"$AUDIENCE","iss":"https://accounts.google.com","iat":$now,"exp":${now + 3600}}"""
        listOf(
            "forged",
            "a.b",
            "a.b.c",
            raw("""{"typ":"JWT"}""", validPayload),
            raw("""{"alg":123}""", validPayload),
            raw("""{"alg":"RS256"}""", """{"aud":"$AUDIENCE","exp":"abc"}"""),
            raw("""{"alg":"RS256"}""", """{"aud":123,"exp":${now + 3600}}"""),
            raw("""{"alg":"RS256"}""", """{"aud":{"x":1},"exp":${now + 3600}}"""),
            raw("""[1,2]""", validPayload),
            raw("""{"alg":"RS256"}""", ""),
        ).forEach { assertEquals(GooglePushAuthResult.REJECTED, authenticator.authenticate(bearer(it)), it) }
    }

    /** 길이가 64바이트가 아닌 ES256 서명은 라이브러리가 IllegalStateException 으로 던진다. */
    @Test
    fun `서명 길이가 틀린 ES256 토큰은 거부한다`() {
        val ecKey = keyPair("EC", 256)
        val ecAuthenticator = OidcGooglePushAuthenticator(properties) { it.setPublicKey(ecKey.public) }
        val now = Instant.now().epochSecond
        val payload = """{"aud":"$AUDIENCE","iss":"https://accounts.google.com","iat":$now,"exp":${now + 3600}}"""

        assertEquals(
            GooglePushAuthResult.REJECTED,
            ecAuthenticator.authenticate(bearer(raw("""{"alg":"ES256"}""", payload, signature = "abc"))),
        )
    }

    @Test
    fun `Google 공개키를 가져오지 못하면 UNAVAILABLE 이다`() {
        val failing =
            OidcGooglePushAuthenticator(properties) {
                it.setHttpTransportFactory(transportFactory { throw IOException("connection reset") })
            }

        assertEquals(GooglePushAuthResult.UNAVAILABLE, failing.authenticate(bearer(token(keyId = "k1"))))
    }

    /** kid 는 요청자가 고르는 값이고 라이브러리 예외 메시지에 그대로 들어간다. 개행을 넣어 가짜 로그 줄을 만들 수 없어야 한다. */
    @Test
    fun `요청자가 고른 kid 로 가짜 로그 줄을 만들 수 없다`(output: CapturedOutput) {
        val jwks = jwks(keyId = "real-key", key = keyPair.public as RSAPublicKey)
        val fetching =
            OidcGooglePushAuthenticator(properties) {
                it.setHttpTransportFactory(transportFactory { json(jwks) })
            }

        val result = fetching.authenticate(bearer(token(keyId = "x\n$FORGED_LINE")))

        assertEquals(GooglePushAuthResult.REJECTED, result)
        // 개행이 ? 로 바뀌어 WARN 한 줄 안에 남는다. 가짜 줄이 ASCII 라서, 개행을 남기는 정리 함수로 바꾸면 아래 단언이 실패한다.
        assertTrue(output.out.lines().any { "RTDN push 토큰 검증에 실패했습니다" in it && "x?$FORGED_LINE" in it })
        assertFalse(output.out.lines().any { it.startsWith(FORGED_LINE) })
    }

    /**
     * 공개키 주소를 Google OAuth2 공개키로 고정한다. 고정하지 않으면 헤더의 alg 를 ES256 으로 바꾼 요청이 서버에 다른 공개키
     * 주소(IAP)를 조회하게 만든다.
     */
    @Test
    fun `헤더의 alg 와 관계없이 Google OAuth2 공개키 주소만 조회한다`() {
        val requested = mutableListOf<String>()
        val jwks = jwks(keyId = "real-key", key = keyPair.public as RSAPublicKey)
        val fetching =
            OidcGooglePushAuthenticator(properties) {
                it.setHttpTransportFactory(transportFactory(requested) { json(jwks) })
            }
        val now = Instant.now().epochSecond
        val payload = """{"aud":"$AUDIENCE","iss":"https://accounts.google.com","iat":$now,"exp":${now + 3600}}"""

        val result = fetching.authenticate(bearer(raw("""{"alg":"ES256","kid":"unknown-key"}""", payload)))

        assertEquals(GooglePushAuthResult.REJECTED, result)
        assertEquals(listOf(OidcGooglePushAuthenticator.GOOGLE_CERTS_URL), requested)
    }

    @Test
    fun `로그에 남길 문자열은 출력 가능한 ASCII 만 남기고 자른다`() {
        assertEquals("a?b?c", "a\nb\u001bc".toLogSafe())
        assertEquals(200, "x".repeat(500).toLogSafe().length)
    }

    @Test
    fun `구독 설정이 없으면 서명이 맞는 토큰도 거부한다`() {
        val unconfigured =
            OidcGooglePushAuthenticator(
                GoogleRtdnProperties(audience = null, serviceAccountEmail = null),
            ) { it.setPublicKey(keyPair.public) }

        assertEquals(GooglePushAuthResult.REJECTED, unconfigured.authenticate(bearer(token())))
    }

    @Test
    fun `운영 생성자도 설정이 없으면 모든 push 를 거부한다`() {
        val unconfigured = OidcGooglePushAuthenticator(GoogleRtdnProperties(audience = AUDIENCE, serviceAccountEmail = " "))

        assertEquals(GooglePushAuthResult.REJECTED, unconfigured.authenticate(bearer(token())))
    }

    private fun bearer(token: String) = "Bearer $token"

    private fun token(
        audience: String = AUDIENCE,
        email: String = PUSH_ACCOUNT,
        issuer: String = "https://accounts.google.com",
        emailVerified: Boolean = true,
        expiresAt: Instant = Instant.now().plusSeconds(3600),
        signer: PrivateKey = keyPair.private,
        keyId: String? = null,
    ): String {
        val header =
            JsonWebSignature
                .Header()
                .setAlgorithm("RS256")
                .setType("JWT")
                .setKeyId(keyId)
        val payload =
            JsonWebToken
                .Payload()
                .setAudience(audience)
                .setIssuer(issuer)
                .setIssuedAtTimeSeconds(expiresAt.epochSecond - 3600)
                .setExpirationTimeSeconds(expiresAt.epochSecond)
                .set("email", email)
                .set("email_verified", emailVerified)
        return JsonWebSignature.signUsingRsaSha256(signer, GsonFactory.getDefaultInstance(), header, payload)
    }

    /** 서명 없이 헤더·본문을 그대로 이어 붙인 토큰. 형식 오류를 만들 때 쓴다. */
    private fun raw(
        header: String,
        payload: String,
        signature: String = "c2ln",
    ): String = "${base64Url(header.toByteArray())}.${base64Url(payload.toByteArray())}.$signature"

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun jwks(
        keyId: String,
        key: RSAPublicKey,
    ): String =
        """{"keys":[{"kid":"$keyId","kty":"RSA","alg":"RS256","use":"sig","n":"${unsigned(
            key.modulus,
        )}","e":"${unsigned(key.publicExponent)}"}]}"""

    private fun unsigned(value: BigInteger): String = base64Url(value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray())

    private fun json(body: String): LowLevelHttpResponse =
        MockLowLevelHttpResponse()
            .setStatusCode(200)
            .setContentType("application/json; charset=UTF-8")
            .setContent(body)

    /** [requested] 에 요청한 주소를 남긴다. */
    private fun transportFactory(
        requested: MutableList<String> = mutableListOf(),
        respond: () -> LowLevelHttpResponse,
    ): HttpTransportFactory {
        val transport =
            object : MockHttpTransport() {
                override fun buildRequest(
                    method: String,
                    url: String,
                ): LowLevelHttpRequest {
                    requested += url
                    return object : MockLowLevelHttpRequest(url) {
                        override fun execute(): LowLevelHttpResponse = respond()
                    }
                }
            }
        return HttpTransportFactory { transport }
    }

    private fun keyPair(
        algorithm: String,
        size: Int,
    ): KeyPair = KeyPairGenerator.getInstance(algorithm).apply { initialize(size) }.generateKeyPair()

    private companion object {
        const val AUDIENCE = "https://dev-api.gamss.kr/api/webhooks/google"
        const val PUSH_ACCOUNT = "rtdn-push@gamss.iam.gserviceaccount.com"

        /** ASCII 만 쓴다. 한글이 섞이면 ASCII 밖의 문자만 바꾸는 정리 함수로도 이 줄이 깨져, 개행이 남아도 단언이 통과한다. */
        const val FORGED_LINE = "2026-11-12T04:10:00.000+09:00 ERROR 1 --- [gamss] forged manual action required"
    }
}
