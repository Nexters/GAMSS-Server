package com.nexters.gamss.llm.settings

import com.nexters.gamss.llm.prompt.PromptType
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun `소재 목록 타입은 시스템 프롬프트로 조립할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { resolver.resolve(PromptType.EONGTTUNG_TOPIC) }
    }

    @Test
    fun `감정 분류 타입은 시스템 프롬프트로 조립할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { resolver.resolve(PromptType.CARD_EMOTION) }
    }

    @Test
    fun `미리보기 조립은 오버라이드를 실제 생성과 같은 형식으로 조립한다`() {
        every { llmSettingsService.currentCommonView() } returns LlmSettingsView("m", "저장 공통")

        val result = resolver.resolveForPreview(PromptType.COMMENT, "공통 시험", "댓글 시험")

        assertEquals("m", result.model)
        assertEquals("공통 시험\n\n댓글 시험", result.systemPrompt)
    }

    @Test
    fun `미리보기 조립에서 null 조각은 저장된 현재값을 쓴다`() {
        every { llmSettingsService.currentCommonView() } returns LlmSettingsView("m", "저장 공통")
        every { llmSettingsService.currentPrompt(PromptType.COMMENT) } returns "저장 댓글"

        assertEquals("저장 공통\n\n저장 댓글", resolver.resolveForPreview(PromptType.COMMENT, null, null).systemPrompt)
        assertEquals("저장 공통\n\n댓글 시험", resolver.resolveForPreview(PromptType.COMMENT, null, "댓글 시험").systemPrompt)
        assertEquals("공통 시험\n\n저장 댓글", resolver.resolveForPreview(PromptType.COMMENT, "공통 시험", null).systemPrompt)
    }

    @Test
    fun `미리보기 조립에 COMMON을 넘기면 실패한다 - 조립할 타입이 없다`() {
        assertFailsWith<IllegalArgumentException> {
            resolver.resolveForPreview(PromptType.COMMON, "공통 시험", null)
        }
    }

    @Test
    fun `미리보기 조립도 소재 목록 타입을 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            resolver.resolveForPreview(PromptType.EONGTTUNG_TOPIC, null, null)
        }
    }

    @Test
    fun `미리보기 조립도 감정 분류 타입을 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            resolver.resolveForPreview(PromptType.CARD_EMOTION, null, null)
        }
    }
}
