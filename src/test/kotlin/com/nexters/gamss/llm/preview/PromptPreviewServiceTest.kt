package com.nexters.gamss.llm.preview

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.config.GeminiPricing
import com.nexters.gamss.llm.config.ModelPricing
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import com.nexters.gamss.llm.generation.CommentGenerationOutput
import com.nexters.gamss.llm.generation.CommentGenerator
import com.nexters.gamss.llm.generation.ReplyGenerationOutput
import com.nexters.gamss.llm.parsing.CommentDraft
import com.nexters.gamss.llm.parsing.CommentFeed
import com.nexters.gamss.llm.parsing.CommentFeedValidator
import com.nexters.gamss.llm.prompt.CommentPromptContext
import com.nexters.gamss.llm.prompt.PromptProvider
import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.llm.selection.EongttungTopicSelector
import com.nexters.gamss.llm.settings.LlmSettingsView
import com.nexters.gamss.llm.settings.SystemPromptResolver
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.service.GenerationLogRecorder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptPreviewServiceTest {
    private val systemPromptResolver = mockk<SystemPromptResolver>()
    private val promptProvider = PromptProvider()
    private val eongttungTopicSelector = mockk<EongttungTopicSelector>()
    private val commentGenerator = mockk<CommentGenerator>()
    private val commentFeedValidator = mockk<CommentFeedValidator>()
    private val geminiPricing =
        GeminiPricing(models = mapOf("test-model" to ModelPricing(inputPer1M = 1.0, cachedInputPer1M = 0.1, outputPer1M = 2.0)))
    private val generationLogRecorder = mockk<GenerationLogRecorder>(relaxed = true)
    private val service =
        PromptPreviewService(
            systemPromptResolver,
            promptProvider,
            eongttungTopicSelector,
            commentGenerator,
            commentFeedValidator,
            geminiPricing,
            generationLogRecorder,
        )

    private val settings = LlmSettingsView("test-model", "조립된 프롬프트")
    private val feed = CommentFeed(listOf(CommentDraft(EmotionType.JOY, "댓글")), emptyList())

    @Test
    fun `캐릭터를 고정하면 그 조건으로 생성하고 결과에 메타데이터를 담는다`() {
        every { systemPromptResolver.resolveForPreview(PromptType.COMMENT, "공통 시험", "댓글 시험") } returns settings
        val context = slot<CommentPromptContext>()
        every { commentGenerator.generateComment(capture(context), settings) } returns
            CommentGenerationOutput(feed, usedTokens = 1_000_000, cachedTokens = 0, inputTokens = 1_000_000, outputTokens = 1_000_000)
        every { commentFeedValidator.validate(feed, listOf(EmotionType.JOY), 0) } returns Unit

        val result =
            service.preview(
                PromptPreviewCommand(
                    commonPrompt = "공통 시험",
                    commentPrompt = "댓글 시험",
                    diaryContent = "샘플 일기",
                    currentConversationSummary = null,
                    pastSummaries = emptyList(),
                    characters = listOf(EmotionType.JOY),
                    tikitakaCount = 0,
                ),
            )

        assertEquals(listOf(EmotionType.JOY), result.characters)
        assertEquals("조립된 프롬프트", result.systemPrompt)
        assertTrue(result.userContent.contains("샘플 일기"))
        assertNotNull(result.feed)
        assertNull(result.validationError)
        assertNull(result.generationError)
        assertEquals(3.0, result.usage.estimatedCostUsd) // 입력 1M×1.0 + 출력 1M×2.0
        assertEquals("샘플 일기", context.captured.diaryContent)
        verify {
            generationLogRecorder.record(
                type = GenerationType.PREVIEW,
                success = true,
                attemptCount = 1,
                latencyMs = any(),
                usedTokens = 1_000_000,
                cachedTokens = 0,
                inputTokens = 1_000_000,
                outputTokens = 1_000_000,
                failureReason = null,
            )
        }
    }

    @Test
    fun `엉뚱이가 등장하면 소재를 골라 결과에 담는다`() {
        every { systemPromptResolver.resolveForPreview(PromptType.COMMENT, null, null) } returns settings
        every { eongttungTopicSelector.select() } returns "배고프다"
        every { commentGenerator.generateComment(any(), settings) } returns CommentGenerationOutput(feed, 10, 0)
        every { commentFeedValidator.validate(any(), any(), any()) } returns Unit

        val result = service.preview(command(characters = listOf(EmotionType.QUIRKY)))

        assertEquals("배고프다", result.eongttungTopic)
        assertTrue(result.userContent.contains("배고프다"))
    }

    @Test
    fun `의미 검증에 실패해도 피드와 함께 실패 사유를 돌려준다`() {
        every { systemPromptResolver.resolveForPreview(PromptType.COMMENT, null, null) } returns settings
        every { commentGenerator.generateComment(any(), settings) } returns CommentGenerationOutput(feed, 10, 0)
        every { commentFeedValidator.validate(any(), any(), any()) } throws CommentGenerationFailedException("캐릭터 구성이 다릅니다")

        val result = service.preview(command(characters = listOf(EmotionType.ANGER)))

        assertNotNull(result.feed)
        assertEquals("캐릭터 구성이 다릅니다", result.validationError)
    }

    @Test
    fun `생성이 실패하면 과금된 토큰과 함께 실패 사유를 돌려준다`() {
        every { systemPromptResolver.resolveForPreview(PromptType.COMMENT, null, null) } returns settings
        every { commentGenerator.generateComment(any(), settings) } throws
            CommentGenerationFailedException("파싱 실패", usedTokens = 500, cachedTokens = 100, inputTokens = 400, outputTokens = 100)

        val result = service.preview(command(characters = listOf(EmotionType.JOY)))

        assertNull(result.feed)
        assertEquals("파싱 실패", result.generationError)
        assertEquals(500, result.usage.usedTokens)
        assertEquals(400, result.usage.inputTokens)
        verify {
            generationLogRecorder.record(
                type = GenerationType.PREVIEW,
                success = false,
                attemptCount = 1,
                latencyMs = any(),
                usedTokens = 500,
                cachedTokens = 100,
                inputTokens = 400,
                outputTokens = 100,
                failureReason = "파싱 실패",
            )
        }
    }

    @Test
    fun `과거 대화 요약을 지정하면 유저 콘텐츠에 섹션이 만들어진다`() {
        every { systemPromptResolver.resolveForPreview(PromptType.COMMENT, null, null) } returns settings
        every { commentGenerator.generateComment(any(), settings) } returns CommentGenerationOutput(feed, 10, 0)
        every { commentFeedValidator.validate(any(), any(), any()) } returns Unit

        val result = service.preview(command(characters = listOf(EmotionType.JOY), pastSummaries = listOf("전에 이직 고민을 나눴다")))

        assertTrue(result.userContent.contains("[과거 대화 요약]"))
        assertTrue(result.userContent.contains("전에 이직 고민을 나눴다"))
    }

    @Test
    fun `캐릭터가 1명인데 티키타카를 지정하면 INVALID_INPUT`() {
        val exception =
            assertFailsWith<BusinessException> {
                service.preview(command(characters = listOf(EmotionType.JOY), tikitakaCount = 1))
            }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `캐릭터를 빈 목록으로 지정하면 INVALID_INPUT`() {
        val exception = assertFailsWith<BusinessException> { service.preview(command(characters = emptyList())) }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `답장 미리보기는 REPLY 조립과 promptId로 그 캐릭터의 재응답을 생성한다`() {
        every { systemPromptResolver.resolveForPreview(PromptType.REPLY, null, "답글 시험") } returns settings
        every {
            commentGenerator.generateReply("원본 일기", "bunno", "화내는 댓글", "고마워", settings)
        } returns ReplyGenerationOutput("재응답", usedTokens = 100, cachedTokens = 0, inputTokens = 80, outputTokens = 20)
        every { commentFeedValidator.validateReply("재응답") } returns Unit

        val result =
            service.previewReply(
                ReplyPreviewCommand(
                    commonPrompt = null,
                    replyPrompt = "답글 시험",
                    diaryContent = "원본 일기",
                    character = EmotionType.ANGER,
                    characterComment = "화내는 댓글",
                    userReply = "고마워",
                ),
            )

        assertEquals("재응답", result.replyText)
        assertEquals(EmotionType.ANGER, result.character)
        assertNull(result.validationError)
        assertNull(result.generationError)
        assertTrue(result.userContent.contains("bunno"))
    }

    @Test
    fun `답장 생성이 실패하면 실패 사유를 담아 돌려준다`() {
        every { systemPromptResolver.resolveForPreview(PromptType.REPLY, null, null) } returns settings
        every {
            commentGenerator.generateReply(any(), any(), any(), any(), settings)
        } throws CommentGenerationFailedException("호출 실패")

        val result =
            service.previewReply(
                ReplyPreviewCommand(
                    commonPrompt = null,
                    replyPrompt = null,
                    diaryContent = "원본 일기",
                    character = EmotionType.JOY,
                    characterComment = "댓글",
                    userReply = "답장",
                ),
            )

        assertNull(result.replyText)
        assertEquals("호출 실패", result.generationError)
    }

    @Test
    fun `답장이 검증에 실패하면 텍스트와 함께 실패 사유를 돌려준다`() {
        every { systemPromptResolver.resolveForPreview(PromptType.REPLY, null, null) } returns settings
        every {
            commentGenerator.generateReply(any(), any(), any(), any(), settings)
        } returns ReplyGenerationOutput("  ", usedTokens = 10, cachedTokens = 0)
        every { commentFeedValidator.validateReply("  ") } throws CommentGenerationFailedException("답글 내용이 비어 있습니다.")

        val result =
            service.previewReply(
                ReplyPreviewCommand(
                    commonPrompt = null,
                    replyPrompt = null,
                    diaryContent = "원본 일기",
                    character = EmotionType.JOY,
                    characterComment = "댓글",
                    userReply = "답장",
                ),
            )

        assertEquals("  ", result.replyText)
        assertEquals("답글 내용이 비어 있습니다.", result.validationError)
    }

    private fun command(
        characters: List<EmotionType>,
        tikitakaCount: Int? = 0,
        pastSummaries: List<String> = emptyList(),
    ): PromptPreviewCommand =
        PromptPreviewCommand(
            commonPrompt = null,
            commentPrompt = null,
            diaryContent = "샘플 일기",
            currentConversationSummary = null,
            pastSummaries = pastSummaries,
            characters = characters,
            tikitakaCount = tikitakaCount,
        )
}
