package com.nexters.gamss.payment.google

import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 어떤 구현이 서는지를 자격증명 유무로 가르는 부분을 검증한다. 자격증명이 있는 쪽은 배포 환경에서만
 * 처음 돌아가는 경로라 여기서 직접 만든 RSA 키로 가짜 서비스 계정을 세워 통과시킨다. 인증 라이브러리는
 * JSON 을 읽기만 하고 Google 에 묻지 않는다.
 */
class GooglePlayConfigTest {
    private val config = GooglePlayConfig()

    @Test
    fun `자격증명이 없으면 요청을 거부하는 구현이 선택된다`() {
        listOf(null, "", "   ").forEach { credentials ->
            val client = config.googlePlayClient(GooglePlayProperties(credentials, packageName = null))

            assertIs<UnconfiguredGooglePlayClient>(client)
            assertFalse(client.configured)
        }
    }

    @Test
    fun `자격증명과 패키지명이 있으면 Google 을 부르는 구현이 선택된다`() {
        val client = config.googlePlayClient(GooglePlayProperties(encoded(serviceAccountJson()), PACKAGE_NAME))

        assertIs<AndroidPublisherGooglePlayClient>(client)
        assertTrue(client.configured)
    }

    @Test
    fun `줄바꿈이 섞인 자격증명도 읽는다`() {
        val wrapped = encoded(serviceAccountJson()).chunked(76).joinToString("\r\n")

        assertIs<AndroidPublisherGooglePlayClient>(config.googlePlayClient(GooglePlayProperties(wrapped, PACKAGE_NAME)))
    }

    @Test
    fun `자격증명은 있는데 패키지명이 없으면 기동을 막는다`() {
        listOf(null, " ").forEach { packageName ->
            assertFailsWith<IllegalStateException> {
                config.googlePlayClient(GooglePlayProperties(encoded(serviceAccountJson()), packageName))
            }
        }
    }

    @Test
    fun `깨진 자격증명이면 기동을 막는다`() {
        assertFailsWith<IllegalArgumentException> { config.googlePlayClient(GooglePlayProperties("not-base64!!", PACKAGE_NAME)) }
        assertFailsWith<Exception> {
            config.googlePlayClient(
                GooglePlayProperties(encoded("""{"type":"authorized_user"}"""), PACKAGE_NAME),
            )
        }
    }

    @Test
    fun `설정을 문자열로 찍어도 자격증명은 드러나지 않는다`() {
        val properties = GooglePlayProperties("c2VjcmV0", PACKAGE_NAME)

        assertFalse(properties.toString().contains("c2VjcmV0"))
    }

    private fun encoded(json: String): String = Base64.getEncoder().encodeToString(json.toByteArray())

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

    private companion object {
        const val PACKAGE_NAME = "com.nexters.gamss.test"
    }
}
