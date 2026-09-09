package com.nexters.gamss.llm.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * AI Studio(Gemini Developer API) 호출 설정. 지금 쓰는 경로가 아니어도 값은 남겨 둔다 —
 * 백오피스에서 언제든 이 경로로 되돌릴 수 있어야 한다.
 */
@ConfigurationProperties(prefix = "gemini.ai-studio")
class GeminiAiStudioProperties(
    val apiKey: String?,
) {
    /** 키가 로그·바인딩 실패 메시지에 찍히지 않게 가린다. */
    override fun toString(): String = "GeminiAiStudioProperties(apiKey=${if (apiKey.isNullOrBlank()) "없음" else "설정됨"})"
}
