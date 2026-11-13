package com.nexters.gamss.llm.generation

import com.nexters.gamss.llm.prompt.JudgePromptContext
import com.nexters.gamss.llm.settings.LlmSettingsView

interface ResponseJudge {
    /** [settings]의 모델과 채점 프롬프트로 한 번 채점한다. 판정과 유형은 [verdicts], [types] 안의 값으로만 나온다. */
    fun judge(
        context: JudgePromptContext,
        settings: LlmSettingsView,
        verdicts: List<String>,
        types: List<String>,
    ): ResponseJudgeOutput
}

data class ResponseJudgeOutput(
    val results: List<JudgedResponse>,
    val usedTokens: Int,
    val cachedTokens: Int,
    val inputTokens: Int,
    val outputTokens: Int,
)

data class JudgedResponse(
    val label: String,
    val evidence: String,
    val reason: String,
    val verdict: String,
    val types: List<String>,
)
