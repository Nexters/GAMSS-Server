package com.nexters.gamss.llm.settings

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.config.GeminiModelCatalog
import com.nexters.gamss.llm.config.GeminiProperties
import com.nexters.gamss.llm.prompt.PromptProvider
import com.nexters.gamss.llm.prompt.PromptType
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

    // ── 모델 ──

    @Test
    fun `COMMON 행이 없으면 코드 기본 모델을 반환한다`() {
        every { repository.findByPromptType(PromptType.COMMON) } returns null

        assertEquals("gemini-3.1-flash-lite", service.currentModel())
    }

    @Test
    fun `COMMON 행이 있으면 그 모델을 반환한다`() {
        every { repository.findByPromptType(PromptType.COMMON) } returns LlmSettings(PromptType.COMMON, "gemini-2.5-flash", "공통P")

        assertEquals("gemini-2.5-flash", service.currentModel())
    }

    @Test
    fun `모델 수정 시 COMMON 행이 없으면 새로 저장한다`() {
        every { modelCatalog.availableModels() } returns listOf("gemini-2.5-flash")
        every { repository.findByPromptType(PromptType.COMMON) } returns null
        every { repository.save(any()) } answers { firstArg() }

        service.updateModel("gemini-2.5-flash")

        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `모델 수정 시 COMMON 행이 있으면 모델만 갱신하고 공통 프롬프트는 보존한다`() {
        val row = LlmSettings(PromptType.COMMON, "gemini-3.1-flash-lite", "공통 프롬프트")
        every { modelCatalog.availableModels() } returns listOf("gemini-2.5-flash")
        every { repository.findByPromptType(PromptType.COMMON) } returns row

        service.updateModel("gemini-2.5-flash")

        assertEquals("gemini-2.5-flash", row.model)
        assertEquals("공통 프롬프트", row.systemPrompt)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `gemini 접두사가 아닌 모델은 INVALID_INPUT`() {
        val exception = assertFailsWith<BusinessException> { service.updateModel("gpt-4") }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `카탈로그에 없는 모델은 INVALID_INPUT`() {
        every { modelCatalog.availableModels() } returns listOf("gemini-2.5-flash")

        val exception = assertFailsWith<BusinessException> { service.updateModel("gemini-9.9-ultra") }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    // ── 프롬프트 ──

    @Test
    fun `프롬프트가 DB에 없으면 타입별 코드 기본값을 반환한다`() {
        every { repository.findByPromptType(PromptType.COMMENT) } returns null
        every { repository.findByPromptType(PromptType.CARD) } returns null

        assertEquals(promptProvider.commentPrompt, service.currentPrompt(PromptType.COMMENT))
        assertEquals(promptProvider.cardPrompt, service.currentPrompt(PromptType.CARD))
    }

    @Test
    fun `프롬프트가 DB에 있으면 그 값을 반환한다`() {
        every { repository.findByPromptType(PromptType.REPLY) } returns LlmSettings(PromptType.REPLY, "gemini-2.5-flash", "커스텀 답글")

        assertEquals("커스텀 답글", service.currentPrompt(PromptType.REPLY))
    }

    @Test
    fun `프롬프트 수정 시 행이 없으면 새로 저장한다`() {
        every { repository.findByPromptType(PromptType.CARD) } returns null
        every { repository.findByPromptType(PromptType.COMMON) } returns null
        every { repository.save(any()) } answers { firstArg() }

        service.updatePrompt(PromptType.CARD, "새 카드 프롬프트")

        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `프롬프트 수정 시 행이 있으면 프롬프트만 갱신하고 모델은 보존한다`() {
        val row = LlmSettings(PromptType.COMMENT, "gemini-2.5-flash", "old")
        every { repository.findByPromptType(PromptType.COMMENT) } returns row

        service.updatePrompt(PromptType.COMMENT, "new")

        assertEquals("new", row.systemPrompt)
        assertEquals("gemini-2.5-flash", row.model)
        verify(exactly = 0) { repository.save(any()) }
    }
}
