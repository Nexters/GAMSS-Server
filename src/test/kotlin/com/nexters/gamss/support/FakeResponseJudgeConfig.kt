package com.nexters.gamss.support

import com.nexters.gamss.llm.generation.JudgedResponse
import com.nexters.gamss.llm.generation.ResponseJudge
import com.nexters.gamss.llm.generation.ResponseJudgeOutput
import com.nexters.gamss.llm.prompt.JudgePromptContext
import com.nexters.gamss.llm.settings.LlmSettingsView
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration(proxyBeanMethods = false)
class FakeResponseJudgeConfig {
    @Bean
    @Primary
    fun responseJudge(): ResponseJudge = FakeResponseJudge()
}

/** 응답에 [AWKWARD_MARK]가 들어 있으면 동문서답으로, 아니면 자연스러움으로 판정한다. */
class FakeResponseJudge : ResponseJudge {
    var calls = 0
    val contexts = mutableListOf<JudgePromptContext>()

    override fun judge(
        context: JudgePromptContext,
        settings: LlmSettingsView,
        verdicts: List<String>,
        types: List<String>,
    ): ResponseJudgeOutput {
        calls++
        contexts += context
        val results =
            context.responses.map {
                if (it.text.contains(AWKWARD_MARK)) {
                    JudgedResponse(it.label, AWKWARD_MARK, "동문서답", "AWKWARD", listOf("OFF_CONTEXT"))
                } else {
                    JudgedResponse(it.label, "", "", "NATURAL", emptyList())
                }
            }
        return ResponseJudgeOutput(results, usedTokens = 7, cachedTokens = 0, inputTokens = 5, outputTokens = 2)
    }

    companion object {
        const val AWKWARD_MARK = "날씨"
    }
}
