package com.nexters.gamss.llm.provider

import com.google.auth.oauth2.GoogleCredentials
import com.google.genai.Client
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.config.GeminiVertexProperties
import org.springframework.stereotype.Component
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
            .credentials(GoogleCredentials.fromStream(credentials()))
            .build()
    }

    override fun client(): Client = lazyClient

    override fun ensureUsable() {
        required(properties.projectId, "gemini.vertex.project-id")
        required(properties.location, "gemini.vertex.location")
        credentials()
    }

    /** 공백을 털고 디코딩한다 — base64 는 76자마다 줄을 바꾸는 구현이 있어 개행이 섞여 들어온다. */
    private fun credentials() =
        Base64
            .getDecoder()
            .decode(required(properties.credentialsBase64, "gemini.vertex.credentials-base64").filterNot { it.isWhitespace() })
            .inputStream()

    private fun required(
        value: String?,
        name: String,
    ): String =
        value?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(ErrorCode.INVALID_INPUT, "Vertex AI 설정이 없습니다: $name")
}
