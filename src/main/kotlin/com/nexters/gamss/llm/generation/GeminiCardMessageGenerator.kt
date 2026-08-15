package com.nexters.gamss.llm.generation
import com.google.genai.Client
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
import com.nexters.gamss.llm.settings.LlmSettingsService
import com.nexters.gamss.llm.settings.LlmSettingsView
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/**
 * Gemini 공식 SDK(google-genai)로 카드 한 줄을 생성하는 [CardMessageGenerator] 구현체.
 * SDK 타입이 이 클래스 밖으로 새어나가지 않는다.
 *
 * 시스템 프롬프트는 [PromptType.CARD] 원본을 그대로 쓴다 — COMMON과 조립하면 캐릭터 보이스 카드가
 * 앞에 붙어 "말투를 철저히 지켜라"와 "캐릭터 말투를 쓰지 마라"가 한 프롬프트 안에서 충돌한다
 * ([GeminiEmotionExtractor]와 같은 이유·같은 방식). 백오피스에서 편집한 값은 재배포 없이 다음
 * 호출부터 반영된다.
 */
@Component
class GeminiCardMessageGenerator(
    private val properties: GeminiProperties,
    private val promptProvider: PromptProvider,
    private val llmSettingsService: LlmSettingsService,
    private val jsonMapper: JsonMapper,
) : CardMessageGenerator {
    private val client: Client by lazy { Client.builder().apiKey(properties.apiKey).build() }

    override fun generate(
        emotion: EmotionType,
        summary: String,
    ): CardMessageOutput =
        // 설정 조회(DB) 실패도 호출 경로 안에서 잡아 재시도 계약을 유지한다.
        generate(emotion, summary) {
            LlmSettingsView(llmSettingsService.currentModel(), llmSettingsService.currentPrompt(PromptType.CARD))
        }

    override fun generate(
        emotion: EmotionType,
        summary: String,
        settings: LlmSettingsView,
    ): CardMessageOutput = generate(emotion, summary) { settings }

    /**
     * 두 경로가 유저 콘텐츠·파싱·토큰 집계를 공유하게 한다. 설정을 값이 아니라 람다로 받는 것은
     * 저장된 설정 조회(DB)도 LLM 호출과 같은 try 안에 들어가야 하기 때문이다.
     */
    private fun generate(
        emotion: EmotionType,
        summary: String,
        settingsSupplier: () -> LlmSettingsView,
    ): CardMessageOutput {
        val response =
            try {
                val settings = settingsSupplier()
                client.models.generateContent(
                    settings.model,
                    promptProvider.buildCardUserContent(emotion, summary),
                    buildConfig(settings.systemPrompt),
                )
            } catch (e: Exception) {
                throw CardGenerationFailedException("카드 한 줄 LLM 호출에 실패했습니다.", e)
            }

        // 토큰은 파싱 전에 뽑는다 — 이후 파싱이 실패해도 이미 과금된 토큰을 실패 로그에 전달할 수 있게 한다.
        val usedTokens = response.usageMetadata().flatMap { it.totalTokenCount() }.orElse(0)
        val cachedTokens = response.usageMetadata().flatMap { it.cachedContentTokenCount() }.orElse(0)
        val inputTokens = response.usageMetadata().flatMap { it.promptTokenCount() }.orElse(0)
        val outputTokens = response.usageMetadata().flatMap { it.candidatesTokenCount() }.orElse(0)

        val text =
            response.text()
                ?: throw CardGenerationFailedException(
                    "카드 한 줄 응답이 비어 있습니다.",
                    usedTokens = usedTokens,
                    cachedTokens = cachedTokens,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                )

        val line =
            try {
                parseSummary(text)
            } catch (e: CardGenerationFailedException) {
                throw CardGenerationFailedException(
                    e.message ?: "카드 한 줄 파싱 실패",
                    e.cause,
                    usedTokens,
                    cachedTokens,
                    inputTokens,
                    outputTokens,
                )
            }
        return CardMessageOutput(line, usedTokens, cachedTokens, inputTokens, outputTokens)
    }

    /**
     * 구조만 검증한다 — 비어 있지 않은 한 줄인지까지다. 길이는 여기서 보지 않는다
     * ([com.nexters.gamss.card.domain.CardSummary]가 자른다) — 길다는 이유로 실패시키면
     * 멀쩡한 문장 하나 때문에 그날 카드가 통째로 안 만들어진다.
     */
    private fun parseSummary(text: String): String {
        val dto =
            try {
                jsonMapper.readValue(text, CardSummaryDto::class.java)
            } catch (e: JacksonException) {
                throw CardGenerationFailedException("카드 한 줄 JSON 파싱에 실패했습니다.", e)
            }
        val line = dto.summary.trim()
        if (line.isBlank() || line.contains('\n') || line.contains('\r')) {
            throw CardGenerationFailedException("카드 한 줄은 비어 있지 않은 한 줄이어야 합니다.")
        }
        return line
    }

    private fun buildConfig(systemPrompt: String): GenerateContentConfig =
        GenerateContentConfig
            .builder()
            .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            .responseMimeType("application/json")
            .responseSchema(cardSummarySchema())
            .httpOptions(HttpOptions.builder().timeout(properties.requestTimeoutMillis))
            .build()

    private fun cardSummarySchema(): Schema =
        Schema
            .builder()
            .type("OBJECT")
            .properties(
                mapOf("summary" to Schema.builder().type("STRING").build()),
            ).required(listOf("summary"))
            .build()

    private data class CardSummaryDto(
        val summary: String = "",
    )
}
