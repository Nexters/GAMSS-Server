package com.nexters.gamss.llm

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.nexters.gamss.emotion.domain.EmotionType
import org.springframework.stereotype.Component

/**
 * Gemini 공식 SDK(google-genai) 구현체. 이 클래스 밖으로는 SDK 타입이 새어나가지 않는다
 * ([CommentGenerator] 인터페이스 뒤에 숨김 — 프로바이더 교체 시 이 파일만 바꾸면 됨). 프롬프트
 * 내용([PromptProvider])과 응답 파싱([CommentFeedJsonParser])은 Gemini 고유가 아니라 우리가 정한
 * 출력 계약이므로 별도 컴포넌트로 분리되어 있다 — 이 클래스는 그 계약을 SDK 호출에 실어 나르는
 * 역할만 한다.
 *
 * 과거 요약(pastSummary)은 아직 실제 소스가 없다 — 프론트에서 나중에 내려줄 예정이라 지금은 항상
 * 빈 값으로 호출된다([com.nexters.gamss.conversation.service.CommentGenerationService] 참고).
 * TODO: 프론트 연동되면 여기 지나가는 pastSummary를 실제 값으로 채우고 PR에 명시할 것.
 */
@Component
class GeminiCommentGenerator(
    private val properties: GeminiProperties,
    private val promptProvider: PromptProvider,
    private val commentFeedJsonParser: CommentFeedJsonParser,
) : CommentGenerator {
    private val client: Client by lazy { Client.builder().apiKey(properties.apiKey).build() }

    override fun generate(
        pastSummary: String,
        diaryContent: String,
        characters: List<EmotionType>,
        tikitakaCount: Int,
        eongttungTopic: String?,
    ): CommentFeed {
        val response =
            try {
                client.models.generateContent(
                    properties.model,
                    promptProvider.buildUserContent(pastSummary, diaryContent, characters, tikitakaCount, eongttungTopic),
                    buildConfig(),
                )
            } catch (e: Exception) {
                throw CommentGenerationFailedException("LLM 호출에 실패했습니다.", e)
            }

        val text =
            response.text()
                ?: throw CommentGenerationFailedException("LLM 응답이 비어 있습니다.")

        return commentFeedJsonParser.parse(text)
    }

    private fun buildConfig(): GenerateContentConfig =
        GenerateContentConfig
            .builder()
            .systemInstruction(Content.fromParts(Part.fromText(promptProvider.systemPrompt)))
            .responseMimeType("application/json")
            .responseSchema(commentFeedSchema())
            .build()

    private fun commentFeedSchema(): Schema =
        Schema
            .builder()
            .type("OBJECT")
            .properties(
                mapOf(
                    "comments" to
                        Schema
                            .builder()
                            .type("ARRAY")
                            .items(commentDraftSchema())
                            .build(),
                    "tikitaka" to
                        Schema
                            .builder()
                            .type("ARRAY")
                            .items(tikitakaDraftSchema())
                            .build(),
                ),
            ).required(listOf("comments", "tikitaka"))
            .build()

    private fun commentDraftSchema(): Schema =
        Schema
            .builder()
            .type("OBJECT")
            .properties(
                mapOf(
                    "character_id" to characterIdSchema(),
                    "text" to Schema.builder().type("STRING").build(),
                ),
            ).required(listOf("character_id", "text"))
            .build()

    private fun tikitakaDraftSchema(): Schema =
        Schema
            .builder()
            .type("OBJECT")
            .properties(
                mapOf(
                    "character_id" to characterIdSchema(),
                    "reply_to" to characterIdSchema(),
                    "text" to Schema.builder().type("STRING").build(),
                ),
            ).required(listOf("character_id", "reply_to", "text"))
            .build()

    private fun characterIdSchema(): Schema =
        Schema
            .builder()
            .type("STRING")
            .enum_(PromptCharacterId.entries.map { it.promptId })
            .build()
}
