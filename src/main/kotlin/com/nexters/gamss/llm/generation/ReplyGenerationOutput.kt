package com.nexters.gamss.llm.generation

/** 답글 생성 LLM 호출 1회의 결과. */
data class ReplyGenerationOutput(
    val text: String,
    val usedTokens: Int,
    val cachedTokens: Int,
)
