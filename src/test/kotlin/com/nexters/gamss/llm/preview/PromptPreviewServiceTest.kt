package com.nexters.gamss.llm.preview

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.config.GeminiPricing
import com.nexters.gamss.llm.config.ModelPricing
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import com.nexters.gamss.llm.generation.CommentGenerationOutput
import com.nexters.gamss.llm.generation.CommentGenerator
import com.nexters.gamss.llm.parsing.CommentDraft
import com.nexters.gamss.llm.parsing.CommentFeed
import com.nexters.gamss.llm.parsing.CommentFeedValidator
import com.nexters.gamss.llm.prompt.CommentPromptContext
import com.nexters.gamss.llm.prompt.PromptProvider
import com.nexters.gamss.llm.selection.CharacterSelection
import com.nexters.gamss.llm.selection.CharacterSelector
import com.nexters.gamss.llm.selection.EongttungTopicSelector
import com.nexters.gamss.llm.settings.LlmSettingsView
import com.nexters.gamss.llm.settings.SystemPromptResolver
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
    private val characterSelector = mockk<CharacterSelector>()
    private val eongttungTopicSelector = mockk<EongttungTopicSelector>()
    private val commentGenerator = mockk<CommentGenerator>()
    private val commentFeedValidator = mockk<CommentFeedValidator>()
    private val geminiPricing =
        GeminiPricing(models = mapOf("test-model" to ModelPricing(inputPer1M = 1.0, cachedInputPer1M = 0.1, outputPer1M = 2.0)))
    private val service =
        PromptPreviewService(
            systemPromptResolver,
            promptProvider,
            characterSelector,
            eongttungTopicSelector,
            commentGenerator,
            commentFeedValidator,
            geminiPricing,
        )

    private val settings = LlmSettingsView("test-model", "조립된 프롬프트")
    private val feed = CommentFeed(listOf(CommentDraft(EmotionType.JOY, "댓글")), emptyList())

    @Test
    fun `캐릭터를 고정하면 그 조건으로 생성하고 결과에 메타데이터를 담는다`() {
        every { systemPromptResolver.resolveForPreview("공통 시험", "댓글 시험") } returns settings
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
        assertEquals(3.0, result.estimatedCostUsd) // 입력 1M×1.0 + 출력 1M×2.0
        assertEquals("샘플 일기", context.captured.diaryContent)
    }

    @Test
    fun `캐릭터를 고정하지 않으면 실제 생성처럼 서버가 무작위로 고른다`() {
        every { characterSelector.select() } returns CharacterSelection(listOf(EmotionType.JOY), 0)
        every { systemPromptResolver.resolveForPreview(null, null) } returns settings
        every { commentGenerator.generateComment(any(), settings) } returns CommentGenerationOutput(feed, 10, 0)
        every { commentFeedValidator.validate(any(), any(), any()) } returns Unit

        val result = service.preview(command(characters = null))

        verify(exactly = 1) { characterSelector.select() }
        assertEquals(listOf(EmotionType.JOY), result.characters)
    }

    @Test
    fun `엉뚱이가 등장하면 소재를 골라 결과에 담는다`() {
        every { systemPromptResolver.resolveForPreview(null, null) } returns settings
        every { eongttungTopicSelector.select() } returns "배고프다"
        every { commentGenerator.generateComment(any(), settings) } returns CommentGenerationOutput(feed, 10, 0)
        every { commentFeedValidator.validate(any(), any(), any()) } returns Unit

        val result = service.preview(command(characters = listOf(EmotionType.QUIRKY)))

        assertEquals("배고프다", result.eongttungTopic)
        assertTrue(result.userContent.contains("배고프다"))
    }

    @Test
    fun `의미 검증에 실패해도 피드와 함께 실패 사유를 돌려준다`() {
        every { systemPromptResolver.resolveForPreview(null, null) } returns settings
        every { commentGenerator.generateComment(any(), settings) } returns CommentGenerationOutput(feed, 10, 0)
        every { commentFeedValidator.validate(any(), any(), any()) } throws CommentGenerationFailedException("캐릭터 구성이 다릅니다")

        val result = service.preview(command(characters = listOf(EmotionType.ANGER)))

        assertNotNull(result.feed)
        assertEquals("캐릭터 구성이 다릅니다", result.validationError)
    }

    @Test
    fun `생성이 실패하면 과금된 토큰과 함께 실패 사유를 돌려준다`() {
        every { systemPromptResolver.resolveForPreview(null, null) } returns settings
        every { commentGenerator.generateComment(any(), settings) } throws
            CommentGenerationFailedException("파싱 실패", usedTokens = 500, cachedTokens = 100, inputTokens = 400, outputTokens = 100)

        val result = service.preview(command(characters = listOf(EmotionType.JOY)))

        assertNull(result.feed)
        assertEquals("파싱 실패", result.generationError)
        assertEquals(500, result.usedTokens)
        assertEquals(400, result.inputTokens)
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

    private fun command(
        characters: List<EmotionType>?,
        tikitakaCount: Int? = 0,
    ): PromptPreviewCommand =
        PromptPreviewCommand(
            commonPrompt = null,
            commentPrompt = null,
            diaryContent = "샘플 일기",
            currentConversationSummary = null,
            characters = characters,
            tikitakaCount = tikitakaCount,
        )
}
