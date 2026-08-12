package com.nexters.gamss.llm.selection

import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.llm.settings.LlmSettingsService
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EongttungTopicSelectorTest {
    private val llmSettingsService = mockk<LlmSettingsService>()
    private val selector = EongttungTopicSelector(llmSettingsService)

    @Test
    fun `DB 소재 목록에서 한 줄을 무작위로 고른다`() {
        every { llmSettingsService.currentPrompt(PromptType.EONGTTUNG_TOPIC) } returns "배고프다\n심심하다\n집 가고 싶다"

        val picked = (1..100).map { selector.select() }.toSet()

        assertTrue(picked.all { it in setOf("배고프다", "심심하다", "집 가고 싶다") })
        // 항상 첫 줄만 돌려주는 고정 구현을 잡는다. 소재 3개×100회가 전부 같을 확률은 사실상 0이다.
        assertTrue(picked.size >= 2)
    }

    @Test
    fun `빈 줄과 앞뒤 공백은 무시한다`() {
        every { llmSettingsService.currentPrompt(PromptType.EONGTTUNG_TOPIC) } returns "  배고프다  \n\n 심심하다\n   "

        repeat(100) {
            assertTrue(selector.select() in setOf("배고프다", "심심하다"))
        }
    }

    @Test
    fun `소재가 모두 비어 있으면 즉시 실패한다`() {
        every { llmSettingsService.currentPrompt(PromptType.EONGTTUNG_TOPIC) } returns "\n   \n"

        assertFailsWith<IllegalStateException> { selector.select() }
    }
}
