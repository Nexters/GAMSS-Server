package com.nexters.gamss.llm.provider

import com.google.auth.oauth2.ServiceAccountCredentials
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.config.GeminiVertexProperties
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 서비스 계정 키를 읽는 두 줄(공백 제거, createScoped)이 이 클래스의 핵심이다. 둘 다 없어도 값이
 * 있는 한 ensureUsable 은 통과해버려서, 전환은 성공하고 그다음 생성부터 전부 죽는 방식으로만
 * 드러난다. 그래서 여기서 직접 붙잡는다.
 */
class VertexAiConnectionTest {
    @Test
    fun `프로젝트 id 가 없으면 어떤 설정인지 알려준다`() {
        val connection = connection(projectId = null)

        val exception = assertFailsWith<BusinessException> { connection.ensureUsable() }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        assertContains(exception.message.orEmpty(), "gemini.vertex.project-id")
    }

    @Test
    fun `자격증명이 없으면 어떤 설정인지 알려준다`() {
        val connection = connection(credentialsBase64 = null)

        val exception = assertFailsWith<BusinessException> { connection.ensureUsable() }

        assertContains(exception.message.orEmpty(), "gemini.vertex.credentials-base64")
    }

    @Test
    fun `base64 가 아니면 base64 문제로 알려준다`() {
        val connection = connection(credentialsBase64 = "이건 base64 가 아니다")

        val exception = assertFailsWith<BusinessException> { connection.ensureUsable() }

        assertContains(exception.message.orEmpty(), "base64 가 아닙니다")
    }

    /**
     * base64 는 구현에 따라 76자마다 줄을 바꾼다(GNU 기본값). 공백을 털지 않으면 멀쩡한 키가
     * "base64 가 아닙니다" 로 거부돼, 전환 자체가 막힌다.
     */
    @Test
    fun `개행이 섞인 base64 도 그대로 받아들인다`() {
        // 서비스 계정 JSON 을 그대로 쓴다. 짧은 값은 base64 가 76자를 못 넘겨 줄바꿈이 하나도 안 생기고,
        // 그러면 이 테스트가 공백 제거를 전혀 검증하지 못한 채 통과한다.
        val wrapped = wrapAt76(encode(serviceAccountJson()))
        assertTrue("\n" in wrapped, "전제가 깨졌다 - 값이 짧아 줄바꿈이 안 생기면 검증할 것이 없다")

        connection(credentialsBase64 = wrapped).ensureUsable()
    }

    /** 공백을 털어도 알파벳 밖의 문자가 남으면 그것은 진짜 base64 문제다. */
    @Test
    fun `공백을 털어도 base64 가 아니면 base64 문제로 알려준다`() {
        val connection = connection(credentialsBase64 = "ab cd\n한글")

        val exception = assertFailsWith<BusinessException> { connection.ensureUsable() }

        assertContains(exception.message.orEmpty(), "base64 가 아닙니다")
    }

    /**
     * 서비스 계정이 아닌 자격증명은 거른다. 엉뚱한 주체로 호출되는 설정 실수를 여기서 잡는다.
     *
     * 형식을 온전히 갖춘 authorized_user 를 쓴다. 필드가 빠진 값은 GoogleCredentials.fromStream 도
     * 못 읽어서, 서비스 계정만 받는다는 규칙이 사라져도 테스트가 그대로 통과한다.
     */
    @Test
    fun `서비스 계정이 아닌 JSON 은 거부한다`() {
        val authorizedUser =
            """
            {
              "type": "authorized_user",
              "client_id": "test-client-id.apps.googleusercontent.com",
              "client_secret": "test-client-secret",
              "refresh_token": "test-refresh-token"
            }
            """.trimIndent()
        val connection = connection(credentialsBase64 = encode(authorizedUser))

        val exception = assertFailsWith<BusinessException> { connection.ensureUsable() }

        assertContains(exception.message.orEmpty(), "읽을 수 없습니다")
    }

    /**
     * SDK 는 직접 넘긴 자격증명에는 스코프를 채워주지 않는다. createScoped 를 빠뜨리면 scope 없는
     * JWT 로 토큰을 요청해 인증이 거부되는데, 그 실패는 배포 후 첫 생성에서야 보인다.
     */
    @Test
    fun `서비스 계정 키에 cloud-platform 스코프를 채운다`() {
        val connection = connection(credentialsBase64 = encode(serviceAccountJson()))

        val credentials = connection.credentials()

        assertFalse(credentials.createScopedRequired(), "스코프가 비어 있으면 안 된다")
        assertContains((credentials as ServiceAccountCredentials).scopes, CLOUD_PLATFORM)
    }

    private fun connection(
        projectId: String? = "gamss-test",
        location: String? = "global",
        credentialsBase64: String? = encode(serviceAccountJson()),
    ) = VertexAiConnection(GeminiVertexProperties(projectId, location, credentialsBase64))

    private fun encode(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray())

    /** GNU base64 가 하듯 76자마다 줄을 바꾼다. */
    private fun wrapAt76(encoded: String): String = encoded.chunked(76).joinToString("\n")

    /**
     * 실제 서비스 계정 JSON 모양. private_key 는 그 자리에서 만든 것이라 아무 권한도 없다.
     * 자격증명 파싱은 키 형식만 보므로 이것으로 충분하다.
     */
    private fun serviceAccountJson(): String {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem =
            "-----BEGIN PRIVATE KEY-----\\n" +
                Base64
                    .getEncoder()
                    .encodeToString(keyPair.private.encoded)
                    .chunked(64)
                    .joinToString("\\n") +
                "\\n-----END PRIVATE KEY-----\\n"
        return """
            {
              "type": "service_account",
              "project_id": "gamss-test",
              "private_key_id": "test-key-id",
              "private_key": "$pem",
              "client_email": "test@gamss-test.iam.gserviceaccount.com",
              "client_id": "1234567890",
              "token_uri": "https://oauth2.googleapis.com/token"
            }
            """.trimIndent()
    }

    companion object {
        private const val CLOUD_PLATFORM = "https://www.googleapis.com/auth/cloud-platform"
    }
}
