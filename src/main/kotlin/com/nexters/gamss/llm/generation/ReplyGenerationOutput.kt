package com.nexters.gamss.llm.generation

import com.nexters.gamss.llm.settings.PromptVersions

/** 답글 생성 LLM 호출 1회의 결과. */
data class ReplyGenerationOutput(
    val text: String,
    val usedTokens: Int,
    val cachedTokens: Int,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val model: String? = null,
    val promptVersions: PromptVersions = PromptVersions.UNKNOWN,
)
