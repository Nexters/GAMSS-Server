package com.nexters.gamss.llm.provider

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.genai.Client
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.config.GeminiVertexProperties
import org.springframework.stereotype.Component
import java.io.IOException
import java.util.Base64

/** 서비스 계정으로 Vertex AI 를 호출한다. 모델 이름은 AI Studio 와 같고 인증 방식만 다르다. */
@Component
class VertexAiConnection(
    private val properties: GeminiVertexProperties,
) : GeminiConnection {
    override val provider = LlmProvider.VERTEX_AI

    private val lazyClient: Client by lazy {
        Client
            .builder()
            .vertexAI(true)
            .project(required(properties.projectId, "gemini.vertex.project-id"))
            .location(required(properties.location, "gemini.vertex.location"))
            .credentials(credentials())
            .build()
    }

    override fun client(): Client = lazyClient

    override fun ensureUsable() {
        required(properties.projectId, "gemini.vertex.project-id")
        required(properties.location, "gemini.vertex.location")
        credentials()
    }

    /** 값이 있는지가 아니라 실제로 읽히는지까지 본다 — 깨진 키로 전환하면 다음 생성부터 전부 죽는다. */
    private fun credentials(): GoogleCredentials {
        val encoded = required(properties.credentialsBase64, "gemini.vertex.credentials-base64")
        // base64 는 76자마다 줄을 바꾸는 구현이 있어 개행이 섞여 들어온다.
        val decoded =
            try {
                Base64.getDecoder().decode(encoded.filterNot { it.isWhitespace() })
            } catch (e: IllegalArgumentException) {
                throw BusinessException(ErrorCode.INVALID_INPUT, "Vertex AI 서비스 계정 키가 base64 가 아닙니다: ${e.message}")
            }
        return try {
            // 서비스 계정만 받는다. GoogleCredentials.fromStream 은 authorized_user 등 다른 형식도
            // 읽어들여, 엉뚱한 주체로 호출되는 설정 실수를 걸러내지 못한다.
            // 스코프도 여기서 채운다 — SDK 는 직접 넘긴 자격증명에는 채워주지 않아(ApplicationDefault
            // 경로에서만 채운다) 그대로 두면 scope 없는 JWT 로 토큰을 요청해 인증이 거부된다.
            ServiceAccountCredentials.fromStream(decoded.inputStream()).createScoped(CLOUD_PLATFORM_SCOPE)
        } catch (e: IOException) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "Vertex AI 서비스 계정 키를 읽을 수 없습니다: ${e.message}")
        }
    }

    private fun required(
        value: String?,
        name: String,
    ): String =
        value?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(ErrorCode.INVALID_INPUT, "Vertex AI 설정이 없습니다: $name")

    companion object {
        private const val CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
    }
}
