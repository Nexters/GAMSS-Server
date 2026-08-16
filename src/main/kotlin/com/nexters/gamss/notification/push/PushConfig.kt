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
 *
 * **없는 것과 틀린 것은 다르게 다룬다.** 비어 있으면 "이 환경은 푸시를 안 쓴다"는 의사표시로 보고
 * 그냥 뜨지만, 값이 있는데 읽지 못하면(깨진 base64·서비스 계정이 아닌 JSON) 예외를 그대로 올려
 * 기동을 막는다. 그러면 헬스체크가 실패하고 배포가 이전 태그로 자동 롤백된다.
 *
 * 읽기 실패를 삼키고 [NoOpPushSender] 로 떨어지는 선택지도 있었지만 택하지 않았다. 푸시는 "안
 * 왔다"가 사용자에게만 보이는 종류라, 잘못된 키가 조용히 묻히면 며칠 뒤 "왜 알림이 안 오지"로
 * 발견된다. 반대 방향의 대가는 그 배포 한 번이 실패하는 것뿐이고(자동 롤백이 받쳐준다) 원인도
 * 기동 로그에 그대로 남는다 — 잘못 넣은 키는 넣은 그 순간에 드러나는 편이 낫다.
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

    /**
     * 공백을 전부 털고 디코딩한다. base64 는 구현에 따라 76자마다 줄을 바꾸고(GNU 기본값), 값이
     * 어디를 거쳐 왔는지에 따라 CRLF 가 섞일 수도 있다. 기본 디코더는 알파벳 밖의 문자를 만나면
     * 거부하므로 **줄바꿈 하나에 기동이 막힌다** — 앞뒤 [String.trim] 만으로는 중간의 개행을 못 지운다.
     *
     * 공백만 지우고 나머지는 그대로 둔다. 알파벳 밖 문자를 통째로 무시하는 디코더(MIME)를 쓰면
     * 진짜 망가진 값도 조용히 통과해 엉뚱한 자격증명 오류로 나타난다.
     */
    private fun decode(credentialsBase64: String) =
        Base64.getDecoder().decode(credentialsBase64.filterNot { it.isWhitespace() }).inputStream()

    companion object {
        private const val APP_NAME = "gamss-push"
    }
}
