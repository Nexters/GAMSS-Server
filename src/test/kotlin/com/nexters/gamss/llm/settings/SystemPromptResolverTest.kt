package com.nexters.gamss.llm.settings
import com.nexters.gamss.llm.prompt.PromptType
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemPromptResolverTest {
    private val llmSettingsService = mockk<LlmSettingsService>()
    private val resolver = SystemPromptResolver(llmSettingsService)

    @Test
    fun `공통 프롬프트와 타입 프롬프트를 조립한다`() {
        every { llmSettingsService.current(PromptType.COMMON) } returns LlmSettingsView("m", "공통규칙")
        every { llmSettingsService.current(PromptType.CARD) } returns LlmSettingsView("gemini-2.5-flash", "카드규칙")

        val result = resolver.resolve(PromptType.CARD)

        assertEquals("gemini-2.5-flash", result.model)
        assertEquals("공통규칙\n\n카드규칙", result.systemPrompt)
    }

    @Test
    fun `COMMON을 넘기면 조립 없이 공통 프롬프트만 반환한다`() {
        every { llmSettingsService.current(PromptType.COMMON) } returns LlmSettingsView("m", "공통규칙")

        val result = resolver.resolve(PromptType.COMMON)

        assertEquals("공통규칙", result.systemPrompt)
    }
}
