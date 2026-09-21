package com.nexters.gamss.llm.generation

import com.google.genai.types.GenerateContentResponse
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.config.GeminiProperties
import com.nexters.gamss.llm.error.CardGenerationFailedException
import com.nexters.gamss.llm.error.LlmFailureKind
import com.nexters.gamss.llm.prompt.PromptProvider
import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.llm.settings.LlmSettingsService
import io.mockk.every
import io.mockk.mockk
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GeminiCardMessageGeneratorTest {
    private val geminiCaller = mockk<GeminiCaller>()
    private val llmSettingsService = mockk<LlmSettingsService>()
    private val generator =
        GeminiCardMessageGenerator(
            GeminiProperties("gemini-3.1-flash-lite", Duration.ofSeconds(15)),
            geminiCaller,
            PromptProvider(),
            llmSettingsService,
            JsonMapper.builder().build(),
        )

    /** LLM이 [text]를 돌려주는 호출. 토큰 메타데이터는 이 테스트들이 보는 대상이 아니라 비워 둔다. */
    private fun stubResponse(text: String) {
        every { llmSettingsService.currentModel() } returns "gemini-3.1-flash-lite"
        every { llmSettingsService.currentPrompt(PromptType.CARD) } returns "카드 프롬프트"
        val response = mockk<GenerateContentResponse>()
        every { response.text() } returns text
        every { response.usageMetadata() } returns Optional.empty()
        every {
            geminiCaller.call(any(), any(), any(), any<(Throwable, LlmFailureKind) -> CardGenerationFailedException>())
        } returns response
    }

    @Test
    fun `판정이 EVENT면 한 줄을 돌려준다`() {
        stubResponse("""{"kind":"EVENT","summary":"오늘 팀장이 자기 할 일을 다 떠넘겼어요"}""")

        val output = generator.generate(EmotionType.ANGER, listOf("팀장이 일 떠넘김"), null)

        assertEquals(CardLineKind.EVENT, output.kind)
        assertEquals("오늘 팀장이 자기 할 일을 다 떠넘겼어요", output.summary)
    }

    @Test
    fun `판정이 NONSENSE면 summary가 비어 있어도 실패가 아니고 한 줄 없이 판정만 돌려준다`() {
        // 쓸 한 줄이 없다는 판정이라 summary가 비어 오는 게 정상이다. 여기서 실패시키면 재시도만 세 번 돌고
        // 그날 카드가 만들어지지 않는다.
        stubResponse("""{"kind":"NONSENSE","summary":""}""")

        val output = generator.generate(EmotionType.ANGER, listOf("ㅊㅊ초쵸ㅛㅊ"), null)

        assertEquals(CardLineKind.NONSENSE, output.kind)
        assertNull(output.summary)
    }

    @Test
    fun `판정이 EVENT인데 한 줄이 비어 있으면 실패로 올린다`() {
        stubResponse("""{"kind":"EVENT","summary":"  "}""")

        assertFailsWith<CardGenerationFailedException> {
            generator.generate(EmotionType.ANGER, listOf("팀장이 일 떠넘김"), null)
        }
    }

    @Test
    fun `지원하지 않는 판정이면 실패로 올린다`() {
        // 스키마의 enum이 막아주지만, 스키마를 지키지 않는 응답이 와도 한 줄로 저장되면 안 된다.
        stubResponse("""{"kind":"MAYBE","summary":"오늘 팀장이 자기 할 일을 다 떠넘겼어요"}""")

        assertFailsWith<CardGenerationFailedException> {
            generator.generate(EmotionType.ANGER, listOf("팀장이 일 떠넘김"), null)
        }
    }
}
