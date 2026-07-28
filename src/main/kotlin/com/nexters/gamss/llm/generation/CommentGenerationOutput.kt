package com.nexters.gamss.llm.generation

import com.nexters.gamss.llm.parsing.CommentFeed

/** LLM 호출 1회의 결과. [usedTokens]는 파싱된 [feed]와 별개로 호출 자체의 메타데이터(과금 단위)다. */
data class CommentGenerationOutput(
    val feed: CommentFeed,
    val usedTokens: Int,
    val cachedTokens: Int,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
)
