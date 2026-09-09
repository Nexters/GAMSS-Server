package com.nexters.gamss.llm.provider

import com.google.genai.Client
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.config.GeminiAiStudioProperties
import org.springframework.stereotype.Component

/** API 키 하나로 AI Studio 를 호출한다. */
@Component
class AiStudioConnection(
    private val properties: GeminiAiStudioProperties,
) : GeminiConnection {
    override val provider = LlmProvider.AI_STUDIO

    private val lazyClient: Client by lazy { Client.builder().apiKey(apiKey()).build() }

    override fun client(): Client = lazyClient

    override fun ensureUsable() {
        apiKey()
    }

    private fun apiKey(): String =
        properties.apiKey?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(ErrorCode.INVALID_INPUT, "AI Studio API 키가 설정되지 않았습니다.")
}
