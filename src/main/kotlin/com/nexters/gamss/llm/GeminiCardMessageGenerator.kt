package com.nexters.gamss.llm

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.nexters.gamss.emotion.domain.EmotionType
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/**
 * Gemini 공식 SDK(google-genai)로 카드 한 줄 대사를 생성하는 [CardMessageGenerator] 구현체.
 * SDK 타입이 이 클래스 밖으로 새어나가지 않는다. 프롬프트([CardPromptProvider])는 대화용과
 * 분리된 카드 전용이고, 이 클래스는 그 계약을 SDK 호출에 실어 나르는 역할만 한다.
 */
@Component
class GeminiCardMessageGenerator(
    private val properties: GeminiProperties,
    private val cardPromptProvider: CardPromptProvider,
    private val jsonMapper: JsonMapper,
) : CardMessageGenerator {
    private val client: Client by lazy { Client.builder().apiKey(properties.apiKey).build() }

    override fun generate(
        emotion: EmotionType,
        summary: String,
    ): CardMessageOutput {
        val response =
            try {
                client.models.generateContent(
                    properties.model,
                    cardPromptProvider.buildUserContent(emotion, summary),
                    buildConfig(),
                )
            } catch (e: Exception) {
                throw CardGenerationFailedException("카드 대사 LLM 호출에 실패했습니다.", e)
            }

        val text =
            response.text()
                ?: throw CardGenerationFailedException("카드 대사 응답이 비어 있습니다.")

        val message = parseLine(text)
        val usedTokens = response.usageMetadata().flatMap { it.totalTokenCount() }.orElse(0)
        return CardMessageOutput(message, usedTokens)
    }

    private fun parseLine(text: String): String {
        val dto =
            try {
                jsonMapper.readValue(text, CardLineDto::class.java)
            } catch (e: JacksonException) {
                throw CardGenerationFailedException("카드 대사 JSON 파싱에 실패했습니다.", e)
            }
        val line = dto.line.trim()
        if (line.isBlank() || line.contains('\n') || line.contains('\r')) {
            throw CardGenerationFailedException("카드 대사는 비어 있지 않은 한 줄이어야 합니다.")
        }
        return line
    }

    private fun buildConfig(): GenerateContentConfig =
        GenerateContentConfig
            .builder()
            .systemInstruction(Content.fromParts(Part.fromText(cardPromptProvider.systemPrompt)))
            .responseMimeType("application/json")
            .responseSchema(cardLineSchema())
            .build()

    private fun cardLineSchema(): Schema =
        Schema
            .builder()
            .type("OBJECT")
            .properties(
                mapOf("line" to Schema.builder().type("STRING").build()),
            ).required(listOf("line"))
            .build()

    private data class CardLineDto(
        val line: String = "",
    )
}
