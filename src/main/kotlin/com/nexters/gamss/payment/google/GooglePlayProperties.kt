package com.nexters.gamss.payment.google

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Google Play Developer API 설정.
 *
 * [credentialsBase64] 는 Play Console 에 연결한 서비스 계정 JSON 을 base64 한 줄로 만든 값이다.
 * `fcm.credentials-base64` 와 같은 방식이다. 비어 있으면 요청을 거부하는 구현이 선택되고([GooglePlayConfig]),
 * 로컬과 테스트는 자격증명 없이 뜬다. 이 값은 앱의 결제 정보를 조회하고 consume 할 수 있는 자격증명이라
 * 설정 파일에 넣지 않는다.
 *
 * [packageName] 은 Android 앱의 applicationId 다. 비밀은 아니지만 환경마다 달라질 수 있어 환경변수로 받는다.
 *
 * Google 호출은 사용자 요청 스레드와 하나뿐인 스케줄링 스레드에서 일어나므로, 타임아웃이 없으면
 * Google 이 느려질 때 둘 다 붙잡힌다. 서버 안에서는 재시도하지 않는다(만료된 액세스 토큰을 바꿔 한 번
 * 다시 보내는 것만 둔다). 앱이 다시 보내고(onResume), consume 은 스케줄러가 다시 시도한다.
 */
@ConfigurationProperties(prefix = "google.play")
class GooglePlayProperties(
    val credentialsBase64: String?,
    val packageName: String?,
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(10),
) {
    /** 자격증명이 로그나 예외 메시지에 찍히지 않게 가린다([com.nexters.gamss.notification.push.FcmProperties] 와 같은 이유). */
    override fun toString(): String =
        "GooglePlayProperties(credentialsBase64=${if (credentialsBase64.isNullOrBlank()) "없음" else "설정됨"}, " +
            "packageName=$packageName, connectTimeout=$connectTimeout, readTimeout=$readTimeout)"
}
