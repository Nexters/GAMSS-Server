package com.nexters.gamss.llm.generation

import com.nexters.gamss.llm.config.GeminiProperties
import com.nexters.gamss.llm.error.CardGenerationFailedException
import com.nexters.gamss.llm.error.LlmFailureKind
import com.nexters.gamss.llm.prompt.PromptProvider
import com.nexters.gamss.llm.settings.LlmSettingsService
import io.mockk.every
import io.mockk.mockk
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeminiEmotionExtractorTest {
    private val geminiCaller = mockk<GeminiCaller>()
    private val promptProvider = mockk<PromptProvider>()
    private val llmSettingsService = mockk<LlmSettingsService>()
    private val extractor =
        GeminiEmotionExtractor(
            GeminiProperties("gemini-3.1-flash-lite", Duration.ofSeconds(15)),
            geminiCaller,
            promptProvider,
            llmSettingsService,
            JsonMapper.builder().build(),
        )

    /**
     * 설정 조회(DB)는 전송 호출보다 먼저 일어나므로 [GeminiCaller] 의 예외 번역이 닿지 않는다. 여기서
     * 종류를 실어주지 않으면 원래 예외가 그대로 빠져나가 [LlmRetryExecutor] 의 `retryOn` 에 걸리지 않고,
     * 재시도도 503 계약도 함께 사라진다.
     */
    @Test
    fun `설정 조회가 실패해도 재시도 대상 실패로 올린다`() {
        every { llmSettingsService.currentModel() } throws IllegalStateException("llm_settings에 COMMON 행이 없습니다.")

        val thrown = assertFailsWith<CardGenerationFailedException> { extractor.extract(listOf("오늘 힘들었어")) }

        assertEquals(LlmFailureKind.CALL, thrown.kind)
    }

    /** 프롬프트 조회도 같은 자리에서 난다. 모델만 감싸고 프롬프트를 빠뜨리면 절반만 고친 것이 된다. */
    @Test
    fun `프롬프트 조회 실패도 같은 종류로 올린다`() {
        every { llmSettingsService.currentModel() } returns "gemini-3.1-flash-lite"
        every { promptProvider.buildCardEmotionUserContent(any()) } returns "유저 콘텐츠"
        every { llmSettingsService.currentPrompt(any()) } throws IllegalStateException("llm_settings에 CARD_EMOTION 행이 없습니다.")

        val thrown = assertFailsWith<CardGenerationFailedException> { extractor.extract(listOf("오늘 힘들었어")) }

        assertEquals(LlmFailureKind.CALL, thrown.kind)
    }
}
