package com.nexters.gamss.llm.generation

import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.HttpOptions
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.config.GeminiProperties
import com.nexters.gamss.llm.error.CardGenerationFailedException
import com.nexters.gamss.llm.prompt.PromptProvider
import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.llm.provider.GeminiConnectionService
import com.nexters.gamss.llm.settings.LlmSettingsService
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/**
 * Gemini 공식 SDK(google-genai)로 유저 메시지의 대표 감정을 분류하는 [EmotionExtractor] 구현체.
 * SDK 타입이 이 클래스 밖으로 새어나가지 않는다([GeminiCardMessageGenerator]와 같은 패턴).
 *
 * 시스템 프롬프트는 분류 작업이라 캐릭터 톤(COMMON)과 조립하지 않고 [PromptType.CARD_EMOTION]
 * 원본을 그대로 쓴다([com.nexters.gamss.llm.settings.SystemPromptResolver]가 조립을 막는다) —
 * 백오피스에서 편집한 값이 재배포 없이 다음 호출부터 반영된다. 응답은 [EmotionType] 이름들의
 * enum 스키마로 강제해 6종 외 값이 아예 생성되지 않게 한다.
 */
@Component
class GeminiEmotionExtractor(
    private val properties: GeminiProperties,
    private val connections: GeminiConnectionService,
    private val promptProvider: PromptProvider,
    private val llmSettingsService: LlmSettingsService,
    private val jsonMapper: JsonMapper,
) : EmotionExtractor {
    override fun extract(userMessages: List<String>): EmotionExtractionOutput {
        val response =
            try {
                connections.activeClient().models.generateContent(
                    llmSettingsService.currentModel(),
                    promptProvider.buildCardEmotionUserContent(userMessages),
                    buildConfig(llmSettingsService.currentPrompt(PromptType.CARD_EMOTION)),
                )
            } catch (e: Exception) {
                throw CardGenerationFailedException("카드 감정 분류 LLM 호출에 실패했습니다.", e)
            }

        // 토큰은 파싱 전에 뽑는다 — 이후 파싱이 실패해도 이미 과금된 토큰을 실패 로그에 전달할 수 있게 한다.
        val usedTokens = response.usageMetadata().flatMap { it.totalTokenCount() }.orElse(0)
        val cachedTokens = response.usageMetadata().flatMap { it.cachedContentTokenCount() }.orElse(0)
        val inputTokens = response.usageMetadata().flatMap { it.promptTokenCount() }.orElse(0)
        val outputTokens = response.usageMetadata().flatMap { it.candidatesTokenCount() }.orElse(0)

        val text =
            response.text()
                ?: throw CardGenerationFailedException(
                    "카드 감정 분류 응답이 비어 있습니다.",
                    usedTokens = usedTokens,
                    cachedTokens = cachedTokens,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                )

        val emotion =
            try {
                parseEmotion(text)
            } catch (e: CardGenerationFailedException) {
                throw CardGenerationFailedException(
                    e.message ?: "카드 감정 분류 파싱 실패",
                    e.cause,
                    usedTokens,
                    cachedTokens,
                    inputTokens,
                    outputTokens,
                )
            }
        return EmotionExtractionOutput(emotion, usedTokens, cachedTokens, inputTokens, outputTokens)
    }

    private fun parseEmotion(text: String): EmotionType {
        val dto =
            try {
                jsonMapper.readValue(text, EmotionDto::class.java)
            } catch (e: JacksonException) {
                throw CardGenerationFailedException("카드 감정 분류 JSON 파싱에 실패했습니다.", e)
            }
        val name = dto.emotion.trim()
        return EmotionType.entries.firstOrNull { it.name == name }
            ?: throw CardGenerationFailedException("카드 감정 분류 결과가 지원하는 감정이 아닙니다: $name")
    }

    private fun buildConfig(systemPrompt: String): GenerateContentConfig =
        GenerateContentConfig
            .builder()
            .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            .responseMimeType("application/json")
            .responseSchema(emotionSchema())
            .httpOptions(HttpOptions.builder().timeout(properties.requestTimeoutMillis))
            .build()

    // 감정 목록을 하드코딩하지 않고 EmotionType에서 만든다 — 감정 종이 바뀌어도 스키마가 따라간다.
    private fun emotionSchema(): Schema =
        Schema
            .builder()
            .type("OBJECT")
            .properties(
                mapOf(
                    "emotion" to
                        Schema
                            .builder()
                            .type("STRING")
                            .enum_(EmotionType.entries.map { it.name })
                            .build(),
                ),
            ).required(listOf("emotion"))
            .build()

    private data class EmotionDto(
        val emotion: String = "",
    )
}
