package com.nexters.gamss.auth.oauth

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.JSONObjectUtils
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPublicKey

/**
 * Firebase(구글 securetoken) 공개키를 x509 인증서 엔드포인트에서 가져오는 JWKSource.
 *
 * 이 엔드포인트는 표준 JWKS 가 아니라 `{kid: PEM인증서}` 형식이라 직접 파싱한다.
 * 응답의 Cache-Control max-age 만큼 캐싱하고, 만료되면 다음 요청 시 갱신한다.
 */
class FirebaseJwkSource(
    private val certsUri: String = DEFAULT_CERTS_URI,
) : JWKSource<SecurityContext> {
    private val httpClient: HttpClient = HttpClient.newHttpClient()

    @Volatile
    private var cached: JWKSet = JWKSet()

    @Volatile
    private var expiresAtMillis: Long = 0

    override fun get(
        jwkSelector: JWKSelector,
        context: SecurityContext?,
    ): List<JWK> = jwkSelector.select(currentKeys())

    private fun currentKeys(): JWKSet {
        if (System.currentTimeMillis() < expiresAtMillis) {
            return cached
        }
        return refresh()
    }

    @Synchronized
    private fun refresh(): JWKSet {
        // 대기하던 다른 스레드가 이미 갱신했으면 재요청하지 않는다.
        if (System.currentTimeMillis() < expiresAtMillis) {
            return cached
        }
        val response = httpClient.send(HttpRequest.newBuilder(URI(certsUri)).GET().build(), HttpResponse.BodyHandlers.ofString())
        cached = toJwkSet(response.body())
        expiresAtMillis = System.currentTimeMillis() + ttlMillis(response)
        return cached
    }

    private fun ttlMillis(response: HttpResponse<String>): Long {
        val cacheControl = response.headers().firstValue("cache-control").orElse("")
        val maxAge =
            MAX_AGE_REGEX
                .find(cacheControl)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
        return (maxAge ?: DEFAULT_TTL_SECONDS) * 1000
    }

    companion object {
        private const val DEFAULT_CERTS_URI =
            "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
        private const val DEFAULT_TTL_SECONDS = 3600L
        private val MAX_AGE_REGEX = Regex("""max-age=(\d+)""")

        /** `{kid: PEM인증서}` JSON 을 JWKSet 으로 변환한다. */
        fun toJwkSet(json: String): JWKSet {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val keys =
                JSONObjectUtils.parse(json).map { (kid, pem) ->
                    val certificate =
                        certificateFactory.generateCertificate(ByteArrayInputStream((pem as String).toByteArray()))
                    RSAKey.Builder(certificate.publicKey as RSAPublicKey).keyID(kid).build() as JWK
                }
            return JWKSet(keys)
        }
    }
}
