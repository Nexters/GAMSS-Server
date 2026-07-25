package com.nexters.gamss.llm

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LlmSettingsServiceTest {
    private val repository = mockk<LlmSettingsRepository>()
    private val geminiProperties = GeminiProperties(apiKey = "k", model = "gemini-3.1-flash-lite")
    private val promptProvider = PromptProvider()
    private val modelCatalog = mockk<GeminiModelCatalog>()
    private val service = LlmSettingsService(repository, geminiProperties, promptProvider, modelCatalog)

    @Test
    fun `DB에 값이 없으면 코드 기본값을 반환한다`() {
        every { repository.findByPromptType(PromptType.COMMENT) } returns null

        val current = service.current(PromptType.COMMENT)

        assertEquals("gemini-3.1-flash-lite", current.model)
        assertEquals(promptProvider.commentPrompt, current.systemPrompt)
    }

    @Test
    fun `REPLY 타입은 DB에 값이 없으면 답글용 코드 기본값을 반환한다`() {
        every { repository.findByPromptType(PromptType.REPLY) } returns null

        val current = service.current(PromptType.REPLY)

        assertEquals("gemini-3.1-flash-lite", current.model)
        assertEquals(promptProvider.replyPrompt, current.systemPrompt)
    }

    @Test
    fun `DB에 값이 있으면 그 값을 반환한다`() {
        every { repository.findByPromptType(PromptType.COMMENT) } returns
            LlmSettings(PromptType.COMMENT, "gemini-2.5-flash", "커스텀 프롬프트")

        val current = service.current(PromptType.COMMENT)

        assertEquals("gemini-2.5-flash", current.model)
        assertEquals("커스텀 프롬프트", current.systemPrompt)
    }

    @Test
    fun `설정이 없으면 새로 저장한다`() {
        every { modelCatalog.availableModels() } returns listOf("gemini-2.5-flash")
        every { repository.findByPromptType(PromptType.COMMENT) } returns null
        every { repository.save(any()) } answers { firstArg() }

        service.update(PromptType.COMMENT, "gemini-2.5-flash", "새 프롬프트")

        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `설정이 있으면 기존 행을 갱신한다`() {
        val row = LlmSettings(PromptType.COMMENT, "gemini-3.1-flash-lite", "old")
        every { modelCatalog.availableModels() } returns listOf("gemini-2.5-flash")
        every { repository.findByPromptType(PromptType.COMMENT) } returns row

        service.update(PromptType.COMMENT, "gemini-2.5-flash", "new")

        assertEquals("gemini-2.5-flash", row.model)
        assertEquals("new", row.systemPrompt)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `gemini 접두사가 아니면 INVALID_INPUT`() {
        val exception = assertFailsWith<BusinessException> { service.update(PromptType.COMMENT, "gpt-4", "p") }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `카탈로그에 없는 모델이면 INVALID_INPUT`() {
        every { modelCatalog.availableModels() } returns listOf("gemini-2.5-flash")

        val exception = assertFailsWith<BusinessException> { service.update(PromptType.COMMENT, "gemini-9.9-ultra", "p") }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `forGeneration은 공통 프롬프트와 타입 프롬프트를 조립한다`() {
        every { repository.findByPromptType(PromptType.COMMON) } returns LlmSettings(PromptType.COMMON, "gemini-3.1-flash-lite", "공통규칙")
        every { repository.findByPromptType(PromptType.CARD) } returns LlmSettings(PromptType.CARD, "gemini-2.5-flash", "카드규칙")

        val generation = service.forGeneration(PromptType.CARD)

        assertEquals("gemini-2.5-flash", generation.model)
        assertEquals("공통규칙\n\n카드규칙", generation.systemPrompt)
    }

    @Test
    fun `forGeneration에 COMMON을 넘기면 공통 프롬프트만 반환한다`() {
        every { repository.findByPromptType(PromptType.COMMON) } returns null

        val generation = service.forGeneration(PromptType.COMMON)

        assertEquals(promptProvider.commonPrompt, generation.systemPrompt)
    }

    @Test
    fun `COMMON은 모델 없이 프롬프트만 저장한다`() {
        every { repository.findByPromptType(PromptType.COMMON) } returns null
        every { repository.save(any()) } answers { firstArg() }

        service.update(PromptType.COMMON, null, "새 공통 프롬프트")

        verify(exactly = 1) { repository.save(any()) }
        verify(exactly = 0) { modelCatalog.availableModels() }
    }

    @Test
    fun `COMMON 외 타입은 모델이 없으면 INVALID_INPUT`() {
        val exception = assertFailsWith<BusinessException> { service.update(PromptType.CARD, null, "p") }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `CARD 타입은 DB에 값이 없으면 카드용 코드 기본값을 반환한다`() {
        every { repository.findByPromptType(PromptType.CARD) } returns null

        val current = service.current(PromptType.CARD)

        assertEquals(promptProvider.cardPrompt, current.systemPrompt)
    }
}
