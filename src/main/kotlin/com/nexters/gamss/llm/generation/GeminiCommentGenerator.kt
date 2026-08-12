package com.nexters.gamss.llm.generation

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.HttpOptions
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.nexters.gamss.llm.config.GeminiProperties
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import com.nexters.gamss.llm.parsing.CommentFeedJsonParser
import com.nexters.gamss.llm.parsing.ReplyJsonParser
import com.nexters.gamss.llm.prompt.CommentPromptContext
import com.nexters.gamss.llm.prompt.PromptCharacterId
import com.nexters.gamss.llm.prompt.PromptProvider
import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.llm.settings.LlmSettingsView
import com.nexters.gamss.llm.settings.SystemPromptResolver
import org.springframework.stereotype.Component

/**
 * Gemini 공식 SDK(google-genai) 구현체. 이 클래스 밖으로는 SDK 타입이 새어나가지 않는다
 * ([CommentGenerator] 인터페이스 뒤에 숨김 — 프로바이더 교체 시 이 파일만 바꾸면 됨). 프롬프트
 * 내용([PromptProvider])과 응답 파싱([CommentFeedJsonParser])은 Gemini 고유가 아니라 우리가 정한
 * 출력 계약이므로 별도 컴포넌트로 분리되어 있다 — 이 클래스는 그 계약을 SDK 호출에 실어 나르는
 * 역할만 한다.
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

    override fun generateComment(context: CommentPromptContext): CommentGenerationOutput {
        // 운영 중 백오피스에서 바꾼 값을 매 호출 반영한다(재배포 불필요).
        // 설정 조회(DB) 실패도 잡아 재시도·FAILED 계약을 유지한다(500·PENDING 고착 방지).
        val settings =
            try {
                systemPromptResolver.resolve(PromptType.COMMENT)
            } catch (e: Exception) {
                throw CommentGenerationFailedException("LLM 호출에 실패했습니다.", e)
            }
        return generateComment(context, settings)
    }

    override fun generateComment(
        context: CommentPromptContext,
        settings: LlmSettingsView,
    ): CommentGenerationOutput {
        val response =
            try {
                client.models.generateContent(
                    settings.model,
                    promptProvider.buildUserContent(context),
                    buildConfig(settings.systemPrompt, commentFeedSchema()),
                )
            } catch (e: Exception) {
                throw CommentGenerationFailedException("LLM 호출에 실패했습니다.", e)
            }

        // 토큰은 파싱 전에 뽑는다 — 이후 파싱이 실패해도 이미 과금된 토큰을 실패 로그에 전달할 수 있게 한다.
        val usedTokens = response.usageMetadata().flatMap { it.totalTokenCount() }.orElse(0)
        val cachedTokens = response.usageMetadata().flatMap { it.cachedContentTokenCount() }.orElse(0)
        val inputTokens = response.usageMetadata().flatMap { it.promptTokenCount() }.orElse(0)
        val outputTokens = response.usageMetadata().flatMap { it.candidatesTokenCount() }.orElse(0)

        val text =
            response.text()
                ?: throw CommentGenerationFailedException(
                    "LLM 응답이 비어 있습니다.",
                    usedTokens = usedTokens,
                    cachedTokens = cachedTokens,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                )

        val feed =
            try {
                commentFeedJsonParser.parse(text)
            } catch (e: CommentGenerationFailedException) {
                throw CommentGenerationFailedException(
                    e.message ?: "댓글 파싱 실패",
                    e.cause,
                    usedTokens,
                    cachedTokens,
                    inputTokens,
                    outputTokens,
                )
            }
        return CommentGenerationOutput(feed, usedTokens, cachedTokens, inputTokens, outputTokens)
    }

    override fun generateReply(
        diaryContent: String,
        characterId: String,
        characterComment: String,
        userReply: String,
    ): ReplyGenerationOutput {
        val settings =
            try {
                systemPromptResolver.resolve(PromptType.REPLY)
            } catch (e: Exception) {
                throw CommentGenerationFailedException("LLM 호출에 실패했습니다.", e)
            }
        return generateReply(diaryContent, characterId, characterComment, userReply, settings)
    }

    override fun generateReply(
        diaryContent: String,
        characterId: String,
        characterComment: String,
        userReply: String,
        settings: LlmSettingsView,
    ): ReplyGenerationOutput {
        val response =
            try {
                client.models.generateContent(
                    settings.model,
                    promptProvider.buildReplyUserContent(diaryContent, characterId, characterComment, userReply),
                    buildConfig(settings.systemPrompt, replySchema()),
                )
            } catch (e: Exception) {
                throw CommentGenerationFailedException("LLM 호출에 실패했습니다.", e)
            }

        val usedTokens = response.usageMetadata().flatMap { it.totalTokenCount() }.orElse(0)
        val cachedTokens = response.usageMetadata().flatMap { it.cachedContentTokenCount() }.orElse(0)
        val inputTokens = response.usageMetadata().flatMap { it.promptTokenCount() }.orElse(0)
        val outputTokens = response.usageMetadata().flatMap { it.candidatesTokenCount() }.orElse(0)

        val text =
            response.text()
                ?: throw CommentGenerationFailedException(
                    "LLM 응답이 비어 있습니다.",
                    usedTokens = usedTokens,
                    cachedTokens = cachedTokens,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                )

        val replyText =
            try {
                replyJsonParser.parse(text)
            } catch (e: CommentGenerationFailedException) {
                throw CommentGenerationFailedException(
                    e.message ?: "답글 파싱 실패",
                    e.cause,
                    usedTokens,
                    cachedTokens,
                    inputTokens,
                    outputTokens,
                )
            }
        return ReplyGenerationOutput(replyText, usedTokens, cachedTokens, inputTokens, outputTokens)
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
            .httpOptions(HttpOptions.builder().timeout(properties.requestTimeoutMillis))
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

    // 신규 생성 대상에서 빠진 캐릭터는 새 댓글 피드에 등장할 수 없으므로 스키마 단계에서 아예 못 뽑게 막는다 —
    // 값 자체가 나올 수 없으면 [CommentFeedValidator]까지 가서 재시도를 태울 일도 없다.
    // 이 스키마는 댓글 피드 전용이라 과거에 다정이가 단 댓글의 답글(replySchema는 text만)·카드 생성은 영향받지 않는다.
    private fun characterIdSchema(): Schema =
        Schema
            .builder()
            .type("STRING")
            .enum_(PromptCharacterId.entries.filter { it.emotionType.selectable }.map { it.promptId })
            .build()
}
