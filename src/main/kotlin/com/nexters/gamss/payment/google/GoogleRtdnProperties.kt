package com.nexters.gamss.payment.google

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * RTDN(실시간 개발자 알림) push 구독 설정. Pub/Sub 인증 push 가 붙이는 OIDC 토큰을 이 값과 비교한다.
 *
 * - [audience]: 토큰의 `aud` 와 한 글자도 다르지 않아야 한다. push 구독에서 audience 를 비워 두면 Google 이 엔드포인트
 *   URL 을 `aud` 로 넣으므로, 그때는 그 URL(예: `https://dev-api.gamss.kr/api/webhooks/google`)을 적는다. 서버 쪽 값은
 *   비워 둘 수 없다. dev 와 prod 구독의 audience 가 달라야 dev 로 가는 토큰을 prod 가 받지 않는다.
 * - [serviceAccountEmail]: push 구독에 설정한 서비스 계정. 토큰의 `email` 이 이 값이어야 한다.
 *
 * 둘 중 하나라도 비어 있으면 모든 push 를 거부한다. 서명만 맞으면 누구의 토큰이든 통과하는 상태가 되기 때문이다.
 * 하나만 들어간 배포는 [GoogleRtdnStartupCheck] 가 기동 직후 ERROR 로 알린다.
 */
@ConfigurationProperties(prefix = "google.play.rtdn")
class GoogleRtdnProperties(
    val audience: String?,
    val serviceAccountEmail: String?,
) {
    val configured: Boolean get() = !audience.isNullOrBlank() && !serviceAccountEmail.isNullOrBlank()
}
