package com.nexters.gamss.notification.push

import com.google.firebase.FirebaseApp
import org.junit.jupiter.api.AfterEach
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * 어떤 구현이 서는지를 **자격증명 유무**로 가르는 부분을 검증한다.
 *
 * 자격증명이 있는 쪽은 배포 환경에서만 처음 돌아가는 경로라, 여기서 잡지 않으면 dev 에 올려봐야
 * 안다. [com.google.auth.oauth2.GoogleCredentials] 는 구글에 물어보지 않고 JSON 을 읽기만 하므로,
 * 직접 만든 RSA 키로 가짜 서비스 계정을 세워 그 경로를 실제로 통과시킨다.
 */
class PushConfigTest {
    private val config = PushConfig()

    @AfterEach
    fun cleanUp() {
        // 초기화된 FirebaseApp 은 JVM 에 남아 다음 테스트가 재사용한다. 매번 같은 조건에서
        // 시작하도록 지운다.
        FirebaseApp.getApps().forEach { it.delete() }
    }

    @Test
    fun `자격증명이 있으면 FCM으로 보내는 구현이 선택된다`() {
        val sender = config.pushSender(FcmProperties(encoded(serviceAccountJson())))

        assertIs<FcmPushSender>(sender)
    }

    /**
     * base64 는 구현에 따라 76자마다 줄을 바꾸고(GNU 기본값), 값이 어디를 거쳐 왔는지에 따라 CRLF 가
     * 섞일 수도 있다. 기본 디코더는 알파벳 밖 문자를 거부하므로 이걸 털지 않으면 기동이 막힌다.
     */
    @Test
    fun `줄바꿈이 섞인 자격증명도 읽는다`() {
        val wrapped = encoded(serviceAccountJson()).chunked(76).joinToString("\r\n")

        assertIs<FcmPushSender>(config.pushSender(FcmProperties(wrapped)))
    }

    @Test
    fun `자격증명이 없으면 보내지 않는 구현이 선택된다`() {
        assertIs<NoOpPushSender>(config.pushSender(FcmProperties(null)))
        assertIs<NoOpPushSender>(config.pushSender(FcmProperties("   ")))
    }

    private fun encoded(json: String): String = Base64.getEncoder().encodeToString(json.toByteArray())

    /**
     * 서비스 계정 JSON 의 최소 형태. 키는 형식만 맞으면 되고(파싱만 한다) 실제로 인증에 쓰이지 않는다.
     */
    private fun serviceAccountJson(): String =
        """
        {
          "type": "service_account",
          "project_id": "gamss-test",
          "private_key_id": "test-key-id",
          "private_key": "${generatePrivateKeyPem().replace("\n", "\\n")}",
          "client_email": "test@gamss-test.iam.gserviceaccount.com",
          "client_id": "1234567890",
          "token_uri": "https://oauth2.googleapis.com/token"
        }
        """.trimIndent()

    private fun generatePrivateKeyPem(): String {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----\n"
    }
}
