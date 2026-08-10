package com.nexters.gamss.llm.preview

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.config.GeminiPricing
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import com.nexters.gamss.llm.generation.CommentGenerationOutput
import com.nexters.gamss.llm.generation.CommentGenerator
import com.nexters.gamss.llm.parsing.CommentFeedValidator
import com.nexters.gamss.llm.prompt.CommentPromptContext
import com.nexters.gamss.llm.prompt.PastSummaries
import com.nexters.gamss.llm.prompt.PromptProvider
import com.nexters.gamss.llm.selection.CharacterSelection
import com.nexters.gamss.llm.selection.CharacterSelector
import com.nexters.gamss.llm.selection.EongttungTopicSelector
import com.nexters.gamss.llm.settings.SystemPromptResolver
import org.springframework.stereotype.Service

/**
 * 백오피스 프롬프트 플레이그라운드: 미저장 프롬프트를 실제 생성 경로(조립 규칙·유저 콘텐츠·
 * LLM 호출·의미 검증) 그대로 시험한다.
 *
 * DB에는 아무것도 남기지 않는다 - 프롬프트 저장은 물론 생성 로그도 기록하지 않아, 실험이
 * 백오피스 사용량·품질 지표를 오염시키지 않는다. 생성 실패·검증 실패도 예외 대신 결과에 담아
 * 돌려준다 - 실패를 관찰하는 것이 이 기능의 목적이기 때문이다.
 */
@Service
class PromptPreviewService(
    private val systemPromptResolver: SystemPromptResolver,
    private val promptProvider: PromptProvider,
    private val characterSelector: CharacterSelector,
    private val eongttungTopicSelector: EongttungTopicSelector,
    private val commentGenerator: CommentGenerator,
    private val commentFeedValidator: CommentFeedValidator,
    private val geminiPricing: GeminiPricing,
) {
    fun preview(command: PromptPreviewCommand): PromptPreviewResult {
        val selection = resolveSelection(command.characters, command.tikitakaCount)
        val eongttungTopic = if (EmotionType.QUIRKY in selection.characters) eongttungTopicSelector.select() else null
        val context =
            CommentPromptContext(
                currentConversationSummary = command.currentConversationSummary,
                pastSummaries = PastSummaries.of(emptyList()),
                diaryContent = command.diaryContent,
                characters = selection.characters,
                tikitakaCount = selection.tikitakaCount,
                eongttungTopic = eongttungTopic,
            )
        val settings = systemPromptResolver.resolveForPreview(command.commonPrompt, command.commentPrompt)
        val userContent = promptProvider.buildUserContent(context)

        val startedAt = System.currentTimeMillis()
        return try {
            val output = commentGenerator.generateComment(context, settings)
            PromptPreviewResult(
                model = settings.model,
                systemPrompt = settings.systemPrompt,
                userContent = userContent,
                characters = selection.characters,
                tikitakaCount = selection.tikitakaCount,
                eongttungTopic = eongttungTopic,
                feed = output.feed,
                validationError = validationError(output, selection),
                generationError = null,
                usedTokens = output.usedTokens,
                cachedTokens = output.cachedTokens,
                inputTokens = output.inputTokens,
                outputTokens = output.outputTokens,
                estimatedCostUsd = geminiPricing.costUsd(settings.model, output.inputTokens, output.cachedTokens, output.outputTokens),
                latencyMs = System.currentTimeMillis() - startedAt,
            )
        } catch (e: CommentGenerationFailedException) {
            // 호출·파싱 실패. 이미 과금된 토큰이 있으면(파싱 실패 등) 그대로 보여준다.
            PromptPreviewResult(
                model = settings.model,
                systemPrompt = settings.systemPrompt,
                userContent = userContent,
                characters = selection.characters,
                tikitakaCount = selection.tikitakaCount,
                eongttungTopic = eongttungTopic,
                feed = null,
                validationError = null,
                generationError = e.message,
                usedTokens = e.usedTokens ?: 0,
                cachedTokens = e.cachedTokens ?: 0,
                inputTokens = e.inputTokens ?: 0,
                outputTokens = e.outputTokens ?: 0,
                estimatedCostUsd = geminiPricing.costUsd(settings.model, e.inputTokens ?: 0, e.cachedTokens ?: 0, e.outputTokens ?: 0),
                latencyMs = System.currentTimeMillis() - startedAt,
            )
        }
    }

    // 캐릭터를 고정하지 않으면 실제 생성처럼 서버가 무작위로 고른다. 고정 선택의 불변식은
    // [CharacterSelection.of]가 보장하고, 여기서는 잘못된 입력을 400으로 번역만 한다.
    private fun resolveSelection(
        characters: List<EmotionType>?,
        tikitakaCount: Int?,
    ): CharacterSelection {
        if (characters == null) {
            return characterSelector.select()
        }
        return try {
            CharacterSelection.of(characters, tikitakaCount ?: 0)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(ErrorCode.INVALID_INPUT, e.message ?: "잘못된 캐릭터 조건입니다.")
        }
    }

    private fun validationError(
        output: CommentGenerationOutput,
        selection: CharacterSelection,
    ): String? =
        try {
            commentFeedValidator.validate(output.feed, selection.characters, selection.tikitakaCount)
            null
        } catch (e: CommentGenerationFailedException) {
            e.message
        }
}
