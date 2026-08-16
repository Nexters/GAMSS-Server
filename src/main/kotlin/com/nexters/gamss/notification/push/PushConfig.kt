package com.nexters.gamss.notification.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Base64

/**
 * 자격증명이 있으면 FCM 으로 보내고, 없으면 보내지 않는 구현을 세운다.
 *
 * 프로필로 가르지 않고 **자격증명 유무**로 가른다. 프로필로 가르면 dev·prod 어느 한쪽에 키를 넣지
 * 않은 채로 배포됐을 때 기동이 실패하거나, 반대로 키가 있는데도 프로필 때문에 안 나가는 상태가
 * 생긴다. 판단 근거를 '보낼 수 있는가' 하나로 두는 편이 어긋날 여지가 적다.
 */
@Configuration
class PushConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun pushSender(properties: FcmProperties): PushSender {
        val credentials = properties.credentialsBase64
        if (credentials.isNullOrBlank()) {
            log.info("FCM 자격증명이 없어 푸시를 보내지 않는다(로컬·테스트 기본값).")
            return NoOpPushSender()
        }
        return FcmPushSender(FirebaseMessaging.getInstance(firebaseApp(credentials)))
    }

    /**
     * 이름을 붙여 초기화한다. 기본 인스턴스를 쓰면 나중에 Firebase 를 다른 용도로 붙일 때 어느
     * 쪽이 먼저 초기화했는지에 따라 설정이 달라진다.
     *
     * 이미 있으면 그것을 쓴다 — 같은 이름으로 두 번 초기화하면 예외가 난다. 테스트가 컨텍스트를
     * 여러 번 띄우는 경우가 이에 해당한다.
     */
    private fun firebaseApp(credentialsBase64: String): FirebaseApp =
        FirebaseApp.getApps().firstOrNull { it.name == APP_NAME } ?: FirebaseApp.initializeApp(
            FirebaseOptions
                .builder()
                .setCredentials(GoogleCredentials.fromStream(decode(credentialsBase64)))
                .build(),
            APP_NAME,
        )

    private fun decode(credentialsBase64: String) = Base64.getDecoder().decode(credentialsBase64.trim()).inputStream()

    companion object {
        private const val APP_NAME = "gamss-push"
    }
}
