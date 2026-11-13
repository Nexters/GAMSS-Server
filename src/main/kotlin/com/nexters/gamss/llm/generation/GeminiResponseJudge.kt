package com.nexters.gamss.llm.generation

import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.HttpOptions
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.nexters.gamss.llm.config.GeminiProperties
import com.nexters.gamss.llm.error.ResponseJudgeFailedException
import com.nexters.gamss.llm.prompt.JudgePromptContext
import com.nexters.gamss.llm.settings.LlmSettingsView
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

@Component
class GeminiResponseJudge(
    private val properties: GeminiProperties,
    private val geminiCaller: GeminiCaller,
    private val jsonMapper: JsonMapper,
) : ResponseJudge {
    override fun judge(
        context: JudgePromptContext,
        settings: LlmSettingsView,
        verdicts: List<String>,
        types: List<String>,
    ): ResponseJudgeOutput {
        val response =
            geminiCaller.call(settings.model, context.toUserContent(), buildConfig(settings.systemPrompt, verdicts, types)) { cause, kind ->
                ResponseJudgeFailedException("응답 채점 LLM 호출에 실패했습니다.", cause, kind = kind)
            }
        val usedTokens = response.usageMetadata().flatMap { it.totalTokenCount() }.orElse(0)
        val cachedTokens = response.usageMetadata().flatMap { it.cachedContentTokenCount() }.orElse(0)
        val inputTokens = response.usageMetadata().flatMap { it.promptTokenCount() }.orElse(0)
        val outputTokens = response.usageMetadata().flatMap { it.candidatesTokenCount() }.orElse(0)

        val text =
            response.text()
                ?: throw ResponseJudgeFailedException("응답 채점 결과가 비어 있습니다.", null, usedTokens, inputTokens, outputTokens)
        val results =
            try {
                jsonMapper.readValue(text, ResultsDto::class.java).results.map {
                    JudgedResponse(it.label, it.evidence, it.reason, it.verdict, it.types)
                }
            } catch (e: JacksonException) {
                throw ResponseJudgeFailedException("응답 채점 JSON 파싱에 실패했습니다.", e, usedTokens, inputTokens, outputTokens)
            }
        return ResponseJudgeOutput(results, usedTokens, cachedTokens, inputTokens, outputTokens)
    }

    private fun buildConfig(
        systemPrompt: String,
        verdicts: List<String>,
        types: List<String>,
    ): GenerateContentConfig =
        GenerateContentConfig
            .builder()
            .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            .temperature(0f)
            .responseMimeType("application/json")
            .responseSchema(resultsSchema(verdicts, types))
            .httpOptions(HttpOptions.builder().timeout(properties.requestTimeoutMillis))
            .build()

    private fun resultsSchema(
        verdicts: List<String>,
        types: List<String>,
    ): Schema {
        val string = Schema.builder().type("STRING").build()
        val item =
            Schema
                .builder()
                .type("OBJECT")
                .properties(
                    mapOf(
                        "label" to string,
                        "evidence" to string,
                        "reason" to string,
                        "verdict" to
                            Schema
                                .builder()
                                .type("STRING")
                                .enum_(verdicts)
                                .build(),
                        "types" to
                            Schema
                                .builder()
                                .type("ARRAY")
                                .items(
                                    Schema
                                        .builder()
                                        .type("STRING")
                                        .enum_(types)
                                        .build(),
                                ).build(),
                    ),
                ).propertyOrdering(listOf("label", "evidence", "reason", "verdict", "types"))
                .required(listOf("label", "evidence", "reason", "verdict", "types"))
                .build()
        return Schema
            .builder()
            .type("OBJECT")
            .properties(
                mapOf(
                    "results" to
                        Schema
                            .builder()
                            .type("ARRAY")
                            .items(item)
                            .build(),
                ),
            ).required(listOf("results"))
            .build()
    }

    private data class ResultsDto(
        val results: List<ResultDto> = emptyList(),
    )

    private data class ResultDto(
        val label: String = "",
        val evidence: String = "",
        val reason: String = "",
        val verdict: String = "",
        val types: List<String> = emptyList(),
    )
}
