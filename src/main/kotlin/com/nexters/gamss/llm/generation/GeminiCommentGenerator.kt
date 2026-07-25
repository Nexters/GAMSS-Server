package com.nexters.gamss.llm.generation
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.config.GeminiProperties
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import com.nexters.gamss.llm.parsing.CommentFeedJsonParser
import com.nexters.gamss.llm.parsing.ReplyJsonParser
import com.nexters.gamss.llm.prompt.PromptCharacterId
import com.nexters.gamss.llm.prompt.PromptProvider
import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.llm.settings.SystemPromptResolver
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
    private val replyJsonParser: ReplyJsonParser,
    private val systemPromptResolver: SystemPromptResolver,
) : CommentGenerator {
    private val client: Client by lazy { Client.builder().apiKey(properties.apiKey).build() }

    override fun generateComment(
        pastSummary: String,
        diaryContent: String,
        characters: List<EmotionType>,
        tikitakaCount: Int,
        eongttungTopic: String?,
    ): CommentGenerationOutput {
        val response =
            try {
                // 운영 중 백오피스에서 바꾼 값을 매 호출 반영한다(재배포 불필요).
                // 설정 조회(DB) 실패도 여기서 잡아 재시도·FAILED 계약을 유지한다(500·PENDING 고착 방지).
                val settings = systemPromptResolver.resolve(PromptType.COMMENT)
                client.models.generateContent(
                    settings.model,
                    promptProvider.buildUserContent(pastSummary, diaryContent, characters, tikitakaCount, eongttungTopic),
                    buildConfig(settings.systemPrompt, commentFeedSchema()),
                )
            } catch (e: Exception) {
                throw CommentGenerationFailedException("LLM 호출에 실패했습니다.", e)
            }

        val text =
            response.text()
                ?: throw CommentGenerationFailedException("LLM 응답이 비어 있습니다.")

        val feed = commentFeedJsonParser.parse(text)
        val usedTokens = response.usageMetadata().flatMap { it.totalTokenCount() }.orElse(0)
        return CommentGenerationOutput(feed, usedTokens)
    }

    override fun generateReply(
        diaryContent: String,
        characterId: String,
        characterComment: String,
        userReply: String,
    ): ReplyGenerationOutput {
        val response =
            try {
                val settings = systemPromptResolver.resolve(PromptType.REPLY)
                client.models.generateContent(
                    settings.model,
                    promptProvider.buildReplyUserContent(diaryContent, characterId, characterComment, userReply),
                    buildConfig(settings.systemPrompt, replySchema()),
                )
            } catch (e: Exception) {
                throw CommentGenerationFailedException("LLM 호출에 실패했습니다.", e)
            }

        val text =
            response.text()
                ?: throw CommentGenerationFailedException("LLM 응답이 비어 있습니다.")

        val replyText = replyJsonParser.parse(text)
        val usedTokens = response.usageMetadata().flatMap { it.totalTokenCount() }.orElse(0)
        return ReplyGenerationOutput(replyText, usedTokens)
    }

    private fun buildConfig(
        systemPrompt: String,
        schema: Schema,
    ): GenerateContentConfig =
        GenerateContentConfig
            .builder()
            .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            .responseMimeType("application/json")
            .responseSchema(schema)
            .build()

    private fun replySchema(): Schema =
        Schema
            .builder()
            .type("OBJECT")
            .properties(mapOf("text" to Schema.builder().type("STRING").build()))
            .required(listOf("text"))
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
