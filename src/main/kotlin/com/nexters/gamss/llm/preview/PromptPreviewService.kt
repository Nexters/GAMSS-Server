package com.nexters.gamss.llm.preview

import com.nexters.gamss.card.domain.CardSummary
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.config.GeminiPricing
import com.nexters.gamss.llm.error.CardGenerationFailedException
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import com.nexters.gamss.llm.generation.CardMessageGenerator
import com.nexters.gamss.llm.generation.CommentGenerationOutput
import com.nexters.gamss.llm.generation.CommentGenerator
import com.nexters.gamss.llm.parsing.CommentFeedValidator
import com.nexters.gamss.llm.prompt.CommentPromptContext
import com.nexters.gamss.llm.prompt.PastSummaries
import com.nexters.gamss.llm.prompt.PromptCharacterId
import com.nexters.gamss.llm.prompt.PromptProvider
import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.llm.selection.CharacterSelection
import com.nexters.gamss.llm.selection.EongttungTopicSelector
import com.nexters.gamss.llm.settings.SystemPromptResolver
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.service.GenerationLogRecorder
import org.springframework.stereotype.Service

/**
 * 백오피스 프롬프트 플레이그라운드: 미저장 프롬프트를 실제 생성 경로(조립 규칙·유저 콘텐츠·
 * LLM 호출·의미 검증) 그대로 시험한다.
 *
 * 프롬프트는 저장하지 않고, 생성 로그만 [GenerationType.PREVIEW]로 남긴다 - 실험에 쓴 실제
 * 과금을 추적하기 위해서다. PREVIEW는 품질 지표 집계에서 제외되고 memberId가 없어 일일 토큰
 * 상한에도 잡히지 않으므로, 실험이 실사용 지표를 오염시키지 않는다. 생성 실패·검증 실패도
 * 예외 대신 결과에 담아 돌려준다 - 실패를 관찰하는 것이 이 기능의 목적이기 때문이다.
 */
@Service
class PromptPreviewService(
    private val systemPromptResolver: SystemPromptResolver,
    private val promptProvider: PromptProvider,
    private val eongttungTopicSelector: EongttungTopicSelector,
    private val commentGenerator: CommentGenerator,
    private val cardMessageGenerator: CardMessageGenerator,
    private val commentFeedValidator: CommentFeedValidator,
    private val geminiPricing: GeminiPricing,
    private val generationLogRecorder: GenerationLogRecorder,
) {
    fun preview(command: PromptPreviewCommand): PromptPreviewResult {
        val selection = resolveSelection(command.characters, command.tikitakaCount)
        val eongttungTopic = if (EmotionType.QUIRKY in selection.characters) eongttungTopicSelector.select() else null
        val context =
            CommentPromptContext(
                currentConversationSummary = command.currentConversationSummary,
                pastSummaries = PastSummaries.of(command.pastSummaries),
                diaryContent = command.diaryContent,
                characters = selection.characters,
                tikitakaCount = selection.tikitakaCount,
                eongttungTopic = eongttungTopic,
            )
        val settings = systemPromptResolver.resolveForPreview(PromptType.COMMENT, command.commonPrompt, command.commentPrompt)
        val userContent = promptProvider.buildUserContent(context)

        val startedAt = System.nanoTime()
        val result =
            try {
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
                    usage = PreviewUsage.of(geminiPricing, settings.model, output),
                    latencyMs = elapsedMs(startedAt),
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
                    usage = PreviewUsage.of(geminiPricing, settings.model, e),
                    latencyMs = elapsedMs(startedAt),
                )
            }
        recordUsage(result.usage, result.latencyMs, result.generationError)
        return result
    }

    /**
     * 유저가 캐릭터 댓글에 답장했을 때 그 캐릭터의 재응답을 시험한다 - 실제 답글 생성 경로
     * (REPLY 조립·promptId·validateReply)를 그대로 쓴다.
     */
    fun previewReply(command: ReplyPreviewCommand): ReplyPreviewResult {
        val settings = systemPromptResolver.resolveForPreview(PromptType.REPLY, command.commonPrompt, command.replyPrompt)
        val promptId = PromptCharacterId.of(command.character).promptId
        val userContent = promptProvider.buildReplyUserContent(command.diaryContent, promptId, command.characterComment, command.userReply)

        val startedAt = System.nanoTime()
        val result =
            try {
                val output =
                    commentGenerator.generateReply(
                        command.diaryContent,
                        promptId,
                        command.characterComment,
                        command.userReply,
                        settings,
                    )
                ReplyPreviewResult(
                    model = settings.model,
                    systemPrompt = settings.systemPrompt,
                    userContent = userContent,
                    character = command.character,
                    replyText = output.text,
                    validationError = replyValidationError(output.text),
                    generationError = null,
                    usage = PreviewUsage.of(geminiPricing, settings.model, output),
                    latencyMs = elapsedMs(startedAt),
                )
            } catch (e: CommentGenerationFailedException) {
                ReplyPreviewResult(
                    model = settings.model,
                    systemPrompt = settings.systemPrompt,
                    userContent = userContent,
                    character = command.character,
                    replyText = null,
                    validationError = null,
                    generationError = e.message,
                    usage = PreviewUsage.of(geminiPricing, settings.model, e),
                    latencyMs = elapsedMs(startedAt),
                )
            }
        recordUsage(result.usage, result.latencyMs, result.generationError)
        return result
    }

    /**
     * 카드 한 줄 생성을 시험한다 - 실제 카드 생성 경로(CARD 단독 프롬프트·유저 콘텐츠·CardSummary
     * 정제)를 그대로 쓴다. 다듬기 전후를 함께 돌려줘 프롬프트의 길이 지시가 지켜지는지 볼 수 있다.
     */
    fun previewCard(command: CardPreviewCommand): CardPreviewResult {
        val settings = systemPromptResolver.resolveStandaloneForPreview(PromptType.CARD, command.cardPrompt)
        val userContent = promptProvider.buildCardUserContent(command.emotion, command.summary)

        val startedAt = System.nanoTime()
        val result =
            try {
                val output = cardMessageGenerator.generate(command.emotion, command.summary, settings)
                val line = CardSummary.normalize(output.summary)
                CardPreviewResult(
                    model = settings.model,
                    systemPrompt = settings.systemPrompt,
                    userContent = userContent,
                    emotion = command.emotion,
                    line = line,
                    rawLine = output.summary,
                    rawLength = output.summary.length,
                    truncated = line != output.summary,
                    generationError = null,
                    usage = PreviewUsage.of(geminiPricing, settings.model, output),
                    latencyMs = elapsedMs(startedAt),
                )
            } catch (e: CardGenerationFailedException) {
                CardPreviewResult(
                    model = settings.model,
                    systemPrompt = settings.systemPrompt,
                    userContent = userContent,
                    emotion = command.emotion,
                    line = null,
                    rawLine = null,
                    rawLength = null,
                    truncated = false,
                    generationError = e.message,
                    usage = PreviewUsage.of(geminiPricing, settings.model, e),
                    latencyMs = elapsedMs(startedAt),
                )
            }
        recordUsage(result.usage, result.latencyMs, result.generationError)
        return result
    }

    // 실험에 쓴 실제 과금을 남긴다. memberId·conversationId가 없어 일일 상한·대화방 집계에는 잡히지 않는다.
    private fun recordUsage(
        usage: PreviewUsage,
        latencyMs: Long,
        generationError: String?,
    ) {
        generationLogRecorder.record(
            type = GenerationType.PREVIEW,
            success = generationError == null,
            attemptCount = 1,
            latencyMs = latencyMs,
            usedTokens = usage.usedTokens,
            cachedTokens = usage.cachedTokens,
            inputTokens = usage.inputTokens,
            outputTokens = usage.outputTokens,
            failureReason = generationError,
        )
    }

    // 벽시계는 NTP 보정에 흔들려 경과 시간이 왜곡될 수 있어 단조 시계를 쓴다.
    private fun elapsedMs(startedAtNanos: Long): Long = (System.nanoTime() - startedAtNanos) / 1_000_000

    private fun replyValidationError(text: String): String? =
        try {
            commentFeedValidator.validateReply(text)
            null
        } catch (e: CommentGenerationFailedException) {
            e.message
        }

    // 선택 불변식은 [CharacterSelection.of]가 보장하고, 여기서는 잘못된 입력을 400으로 번역만 한다.
    private fun resolveSelection(
        characters: List<EmotionType>,
        tikitakaCount: Int?,
    ): CharacterSelection =
        try {
            CharacterSelection.of(characters, tikitakaCount ?: 0)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(ErrorCode.INVALID_INPUT, e.message ?: "잘못된 캐릭터 조건입니다.")
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
