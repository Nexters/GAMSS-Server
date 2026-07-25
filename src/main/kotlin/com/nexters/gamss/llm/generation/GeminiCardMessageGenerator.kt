package com.nexters.gamss.llm.generation
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.config.GeminiProperties
import com.nexters.gamss.llm.error.CardGenerationFailedException
import com.nexters.gamss.llm.prompt.PromptProvider
import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.llm.settings.SystemPromptResolver
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/**
 * Gemini 공식 SDK(google-genai)로 카드 한 줄 대사를 생성하는 [CardMessageGenerator] 구현체.
 * SDK 타입이 이 클래스 밖으로 새어나가지 않는다. 모델·시스템 프롬프트는 [SystemPromptResolver]가
 * 공통 프롬프트 + 카드 프롬프트로 조립한 값을 쓰므로(백오피스에서 편집 가능), 이 클래스는 그 계약을
 * SDK 호출에 실어 나르는 역할만 한다.
 */
@Component
class GeminiCardMessageGenerator(
    private val properties: GeminiProperties,
    private val promptProvider: PromptProvider,
    private val systemPromptResolver: SystemPromptResolver,
    private val jsonMapper: JsonMapper,
) : CardMessageGenerator {
    private val client: Client by lazy { Client.builder().apiKey(properties.apiKey).build() }

    override fun generate(
        emotion: EmotionType,
        summary: String,
    ): CardMessageOutput {
        val response =
            try {
                // 운영 중 백오피스에서 바꾼 값(공통 + 카드)을 매 호출 조립·반영한다(재배포 불필요).
                // 설정 조회(DB) 실패도 여기서 잡아 재시도 계약을 유지한다.
                val settings = systemPromptResolver.resolve(PromptType.CARD)
                client.models.generateContent(
                    settings.model,
                    promptProvider.buildCardUserContent(emotion, summary),
                    buildConfig(settings.systemPrompt),
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

    private fun buildConfig(systemPrompt: String): GenerateContentConfig =
        GenerateContentConfig
            .builder()
            .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
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
