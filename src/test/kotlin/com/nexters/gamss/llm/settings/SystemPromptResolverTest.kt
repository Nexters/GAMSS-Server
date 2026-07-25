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
    fun `단일 모델과 공통+타입 조립 프롬프트를 돌려준다`() {
        every { llmSettingsService.currentCommonView() } returns LlmSettingsView("gemini-2.5-flash", "공통규칙")
        every { llmSettingsService.currentPrompt(PromptType.CARD) } returns "카드규칙"

        val result = resolver.resolve(PromptType.CARD)

        assertEquals("gemini-2.5-flash", result.model)
        assertEquals("공통규칙\n\n카드규칙", result.systemPrompt)
    }

    @Test
    fun `COMMON을 넘기면 조립 없이 공통 프롬프트만 반환한다`() {
        every { llmSettingsService.currentCommonView() } returns LlmSettingsView("m", "공통규칙")

        val result = resolver.resolve(PromptType.COMMON)

        assertEquals("공통규칙", result.systemPrompt)
    }
}
