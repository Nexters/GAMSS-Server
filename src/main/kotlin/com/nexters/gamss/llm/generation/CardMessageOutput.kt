package com.nexters.gamss.llm.generation

/** 카드 대사 생성 1회의 결과. [usedTokens]는 호출 자체의 과금 단위다. */
data class CardMessageOutput(
    val message: String,
    val usedTokens: Int,
    val cachedTokens: Int,
)
