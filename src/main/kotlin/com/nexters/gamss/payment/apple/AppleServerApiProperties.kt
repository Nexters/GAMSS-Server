package com.nexters.gamss.payment.apple

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * App Store Server API(In-App Purchase 키) 설정. 셋 다 비어 있으면 알림 기록 배치가 꺼진다. 일부만 있으면 기동이 실패한다.
 *
 * [environment] 는 이 서버가 알림을 받는 환경이다. Apple 은 Production 알림을 prod 로, Sandbox 알림을 dev 로 보내므로
 * prod 는 `PRODUCTION`, dev 는 `SANDBOX` 다.
 */
@ConfigurationProperties(prefix = "apple.iap.server-api")
class AppleServerApiProperties(
    /** `.p8` 파일 내용을 base64 로 인코딩한 한 줄. */
    val signingKeyBase64: String = "",
    val keyId: String = "",
    val issuerId: String = "",
    val environment: String = "",
) {
    val configured: Boolean get() = signingKeyBase64.isNotBlank() || keyId.isNotBlank() || issuerId.isNotBlank()
}
