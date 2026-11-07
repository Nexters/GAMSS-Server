package com.nexters.gamss.payment.google

import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.auth.oauth2.ServiceAccountCredentials
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Base64
import com.google.auth.http.HttpCredentialsAdapter as CredentialsRequestInitializer

/**
 * 자격증명이 있으면 Google Play 를 부르는 구현을, 없으면 요청을 거부하는 구현을 세운다.
 *
 * [com.nexters.gamss.notification.push.PushConfig] 와 같은 원칙을 따른다. 프로필이 아니라 자격증명
 * 유무로 가르고, **없는 것과 틀린 것은 다르게 다룬다.** 비어 있으면 "이 환경은 Android 결제를 받지
 * 않는다"로 보고 뜬다. 값이 있는데 읽지 못하면(깨진 base64, 서비스 계정이 아닌 JSON) 예외를 그대로
 * 올려 기동을 막는다. 자격증명은 있는데 패키지명이 없을 때도 막는다. 그대로 뜨면 모든 구매 조회가
 * 실패하는데, 그 상태는 사용자 결제가 들어와야 드러난다. 기동이 실패하면 헬스체크가 배포를 이전 태그로
 * 되돌리고 원인이 기동 로그에 남는다.
 */
@Configuration
class GooglePlayConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun googlePlayClient(properties: GooglePlayProperties): GooglePlayClient {
        val credentials = properties.credentialsBase64
        if (credentials.isNullOrBlank()) {
            log.info("Google Play 자격증명이 없어 Android 구매 검증 요청을 거부한다(로컬·테스트 기본값).")
            return UnconfiguredGooglePlayClient()
        }
        val packageName = properties.packageName
        check(!packageName.isNullOrBlank()) {
            "Google Play 자격증명은 있는데 패키지명(google.play.package-name)이 비어 있습니다."
        }
        return AndroidPublisherGooglePlayClient(publisher(credentials, properties), packageName)
    }

    private fun publisher(
        credentialsBase64: String,
        properties: GooglePlayProperties,
    ): AndroidPublisher {
        // 액세스 토큰 갱신은 인증 라이브러리가 자기 요청으로 따로 보내므로 아래 타임아웃이 붙지 않는다
        // (라이브러리 기본값 연결 20초·읽기 20초). 그 요청은 실패하면 기본으로 세 번 더 시도해, 토큰
        // 엔드포인트가 멈추면 호출 한 번이 1분 넘게 걸린다. 서버 안에서는 재시도하지 않는다는 원칙대로
        // 끄고, 한 번의 상한을 약 40초로 둔다. 그보다 짧게 하려면 transport 를 감싸야 하는데 NetHttpTransport 가
        // final 이라 하지 않았다. 스케줄러는 장애가 보이면 회차를 멈춰 이 대기가 반복되지 않게 한다.
        val credentials =
            ServiceAccountCredentials
                .fromStream(decode(credentialsBase64))
                .createWithCustomRetryStrategy(false)
                .createScoped(AndroidPublisherScopes.ANDROIDPUBLISHER)
        val authorizer = CredentialsRequestInitializer(credentials)
        val initializer =
            HttpRequestInitializer { request ->
                // 인증을 먼저 붙인다. 이 초기화가 401 응답 때 토큰을 갱신하는 처리도 함께 건다.
                authorizer.initialize(request)
                request.connectTimeout = properties.connectTimeout.toMillis().toInt()
                request.readTimeout = properties.readTimeout.toMillis().toInt()
                // 401 이면 토큰을 갱신하고 다시 보내는데, 횟수 기본값이 10 이라 401 이 계속 오면 토큰 발급과
                // 호출을 열 번 넘게 반복한다. 만료된 토큰을 한 번 바꿔 보내는 데까지만 둔다.
                request.numberOfRetries = 1
            }
        return AndroidPublisher
            .Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), initializer)
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    /** 공백을 전부 털고 디코딩한다. 이유는 [com.nexters.gamss.notification.push.PushConfig] 의 decode 와 같다. */
    private fun decode(credentialsBase64: String) =
        Base64.getDecoder().decode(credentialsBase64.filterNot { it.isWhitespace() }).inputStream()

    private companion object {
        const val APPLICATION_NAME = "gamss-server"
    }
}
