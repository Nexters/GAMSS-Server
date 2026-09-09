package com.nexters.gamss.llm.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Vertex AI 호출 설정. 요금이 GCP 결제 계정으로 잡혀 무료 크레딧을 쓸 수 있다.
 *
 * [credentialsBase64] 는 서비스 계정 JSON 을 base64 로 한 줄로 만든 값이다 — 원본 JSON 은 여러 줄이라
 * 배포가 시크릿을 전달하는 `.env` 한 줄에 담기지 않는다(FcmProperties 와 같은 이유).
 */
@ConfigurationProperties(prefix = "gemini.vertex")
class GeminiVertexProperties(
    val projectId: String?,
    val location: String?,
    val credentialsBase64: String?,
) {
    override fun toString(): String =
        "GeminiVertexProperties(projectId=$projectId, location=$location, " +
            "credentialsBase64=${if (credentialsBase64.isNullOrBlank()) "없음" else "설정됨"})"
}
