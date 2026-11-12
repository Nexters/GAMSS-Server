package com.nexters.gamss.payment.google

import com.google.auth.oauth2.TokenVerifier
import com.google.common.util.concurrent.UncheckedExecutionException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutionException

/** Pub/Sub 인증 push 요청의 `Authorization` 헤더를 검증한다. */
fun interface GooglePushAuthenticator {
    fun authenticate(authorizationHeader: String?): GooglePushAuthResult
}

enum class GooglePushAuthResult {
    AUTHENTICATED,

    /** 헤더가 없거나, Bearer 형식이 아니거나, 토큰이 비어 있다. 401 로 응답한다. */
    MISSING,

    /** 토큰은 있는데 형식·서명·audience·서비스 계정·발급자 중 하나가 틀렸다. 403 으로 응답한다. */
    REJECTED,

    /** Google 공개키를 가져오지 못해 토큰을 판단할 수 없다. 토큰 문제가 아닌 일시 장애라 503 으로 응답한다. */
    UNAVAILABLE,
}

/**
 * Google 이 서명한 OIDC 토큰을 검증한다. 서명(Google 공개키), 만료, `aud` 는 [TokenVerifier] 가 보고,
 * `iss`·`email`·`email_verified` 는 여기서 본다. [TokenVerifier] 는 발급자를 하나만 받는데 Google 은 두 형식을
 * 쓰므로 발급자는 직접 비교한다.
 *
 * 검증기는 이 클래스가 설정값으로 만든다. audience 까지 테스트가 넣게 두면 운영 생성자에서 audience 설정이 빠져도
 * 테스트가 통과하기 때문이다. 테스트는 생성자의 `customizeVerifier` 로 공개키나 HTTP 전송만 바꾼다.
 *
 * 공개키 주소는 Google OAuth2 공개키([GOOGLE_CERTS_URL])로 고정한다. Pub/Sub 토큰은 RS256 이라 동작은 같고, 요청자가
 * 헤더의 alg 로 다른 공개키 주소(IAP 의 ES256 키)를 고르게 두지 않으려는 것이다. 공개키는 라이브러리가 1시간 캐시하고,
 * 조회는 요청 스레드에서 일어난다. 응답이 2xx 가 아니면 라이브러리가 몇 초 동안 두 번 더 시도하고, 응답이 없으면
 * 연결·읽기 20초 기본값까지 기다린다. [TokenVerifier] 에는 이 값을 바꿀 설정이 없어 받아들인다
 * ([GooglePlayConfig] 의 액세스 토큰 갱신과 같은 사정).
 *
 * 로그에는 토큰을 남기지 않는다. 라이브러리 예외 메시지에는 요청자가 고른 값(헤더의 kid 등)이 들어갈 수 있으므로,
 * 출력 가능한 ASCII 만 남기고 길이를 자른다. 개행을 넣어 가짜 로그 줄을 만드는 것을 막는다.
 */
@Component
class OidcGooglePushAuthenticator internal constructor(
    private val properties: GoogleRtdnProperties,
    customizeVerifier: (TokenVerifier.Builder) -> Unit,
) : GooglePushAuthenticator {
    @Autowired
    constructor(properties: GoogleRtdnProperties) : this(properties, {})

    private val log = LoggerFactory.getLogger(javaClass)

    /** 설정이 없으면 null 이고, 그때는 모든 push 를 거부한다. */
    private val verifier: TokenVerifier? =
        if (properties.configured) {
            TokenVerifier
                .newBuilder()
                .setAudience(requireNotNull(properties.audience))
                .setCertificatesLocation(GOOGLE_CERTS_URL)
                .also(customizeVerifier)
                .build()
        } else {
            null
        }

    override fun authenticate(authorizationHeader: String?): GooglePushAuthResult {
        val token =
            authorizationHeader
                ?.takeIf { it.startsWith(BEARER_PREFIX) }
                ?.removePrefix(BEARER_PREFIX)
                ?.trim()
        if (token.isNullOrEmpty()) {
            // 헤더 값은 남기지 않는다. push 구독에서 인증을 켜지 않으면 모든 push 가 이 경로로 온다.
            log.warn("RTDN push 요청에 Bearer 토큰이 없습니다. push 구독의 인증 설정을 확인하세요.")
            return GooglePushAuthResult.MISSING
        }
        if (verifier == null) {
            log.error("RTDN push 구독 설정(google.play.rtdn.audience, service-account-email)이 없어 요청을 거부했습니다.")
            return GooglePushAuthResult.REJECTED
        }
        val payload =
            try {
                verifier.verify(token).payload
            } catch (e: TokenVerifier.VerificationException) {
                if (e.cause is ExecutionException || e.cause is UncheckedExecutionException) {
                    log.warn("RTDN push 검증용 Google 공개키를 가져오지 못했습니다. 503 으로 응답해 다시 받습니다.", e)
                    return GooglePushAuthResult.UNAVAILABLE
                }
                log.warn("RTDN push 토큰 검증에 실패했습니다: {}", e.message?.toLogSafe())
                return GooglePushAuthResult.REJECTED
            } catch (e: RuntimeException) {
                // 형식이 깨진 토큰이다. 라이브러리는 점 구조·base64·alg 누락·클레임 타입 오류(IllegalArgumentException)와
                // 길이가 틀린 ES256 서명(IllegalStateException)을 감싸지 않고 그대로 던진다. 이 try 안에는 라이브러리 호출만
                // 있으므로 RuntimeException 을 모두 형식 오류로 본다. 그대로 두면 익명 요청이 500 과 ERROR 스택트레이스를 만든다.
                log.warn("RTDN push 토큰 형식이 올바르지 않습니다: {}", e.javaClass.simpleName)
                return GooglePushAuthResult.REJECTED
            }
        val issuerOk = payload.issuer in ISSUERS
        val emailOk = payload["email"] == properties.serviceAccountEmail
        val verified = payload["email_verified"].let { it == true || it == "true" }
        if (!issuerOk || !emailOk || !verified) {
            log.warn("RTDN push 토큰의 발급자나 서비스 계정이 다릅니다: issuerOk={}, emailOk={}, emailVerified={}", issuerOk, emailOk, verified)
            return GooglePushAuthResult.REJECTED
        }
        return GooglePushAuthResult.AUTHENTICATED
    }

    internal companion object {
        const val BEARER_PREFIX = "Bearer "
        const val GOOGLE_CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs"
        val ISSUERS = setOf("accounts.google.com", "https://accounts.google.com")

        /** 공백(0x20)부터 ~(0x7E)까지가 아닌 문자. 개행과 제어 문자, ASCII 밖의 문자다. */
        private val NON_PRINTABLE = Regex("[^ -~]")
        private const val MAX_LOG_LENGTH = 200

        fun String.toLogSafe(): String = replace(NON_PRINTABLE, "?").take(MAX_LOG_LENGTH)
    }
}
