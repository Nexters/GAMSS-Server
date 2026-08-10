package com.nexters.gamss.llm.settings

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.config.GeminiModelCatalog
import com.nexters.gamss.llm.config.GeminiProperties
import com.nexters.gamss.llm.prompt.PromptType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LlmSettingsServiceTest {
    private val repository = mockk<LlmSettingsRepository>()
    private val geminiProperties =
        GeminiProperties(apiKey = "k", model = "gemini-3.1-flash-lite", requestTimeout = Duration.ofSeconds(30))
    private val modelCatalog = mockk<GeminiModelCatalog>()
    private val service = LlmSettingsService(repository, geminiProperties, modelCatalog)

    // ── 모델 ──

    @Test
    fun `COMMON 행의 모델을 반환한다`() {
        every { repository.findByPromptType(PromptType.COMMON) } returns LlmSettings(PromptType.COMMON, "gemini-2.5-flash", "공통P")

        assertEquals("gemini-2.5-flash", service.currentModel())
    }

    @Test
    fun `모델 수정 시 모델만 갱신하고 공통 프롬프트는 보존한다`() {
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
    fun `프롬프트는 DB 행의 값을 반환한다`() {
        every { repository.findByPromptType(PromptType.REPLY) } returns LlmSettings(PromptType.REPLY, "gemini-2.5-flash", "커스텀 답글")

        assertEquals("커스텀 답글", service.currentPrompt(PromptType.REPLY))
    }

    @Test
    fun `행이 없으면 시딩 누락이므로 어느 경로든 즉시 실패한다`() {
        every { repository.findByPromptType(any()) } returns null
        every { modelCatalog.availableModels() } returns emptyList()

        assertFailsWith<IllegalStateException> { service.currentPrompt(PromptType.CARD) }
        assertFailsWith<IllegalStateException> { service.currentModel() }
        assertFailsWith<IllegalStateException> { service.updateModel("gemini-2.5-flash") }
        assertFailsWith<IllegalStateException> { service.updatePrompt(PromptType.CARD, "새 값") }
        assertFailsWith<IllegalStateException> { service.currentCommonView() }
    }

    @Test
    fun `프롬프트 수정 시 프롬프트만 갱신하고 모델은 보존한다`() {
        val row = LlmSettings(PromptType.COMMENT, "gemini-2.5-flash", "old")
        every { repository.findByPromptType(PromptType.COMMENT) } returns row

        service.updatePrompt(PromptType.COMMENT, "new")

        assertEquals("new", row.systemPrompt)
        assertEquals("gemini-2.5-flash", row.model)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `공통 뷰는 COMMON 행의 모델과 프롬프트를 함께 돌려준다`() {
        every { repository.findByPromptType(PromptType.COMMON) } returns LlmSettings(PromptType.COMMON, "gemini-2.5-flash", "공통P")

        val view = service.currentCommonView()

        assertEquals("gemini-2.5-flash", view.model)
        assertEquals("공통P", view.systemPrompt)
    }
}
