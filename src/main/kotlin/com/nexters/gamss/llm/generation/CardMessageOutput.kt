package com.nexters.gamss.llm.generation

/**
 * 카드 한 줄 생성 1회의 결과. [usedTokens]는 호출 자체의 과금 단위다.
 *
 * [summary]는 구조만 검증된 값이다(한 줄·비어 있지 않음). 길이를 맞추는 것은
 * [com.nexters.gamss.card.domain.CardSummary]의 몫이라 여기서는 자르지 않는다.
 */
data class CardMessageOutput(
    val summary: String,
    val usedTokens: Int,
    val cachedTokens: Int,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
)
