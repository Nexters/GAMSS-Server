package com.nexters.gamss.llm.generation

import com.nexters.gamss.emotion.domain.EmotionType

/** 감정 분류 1회의 결과. [usedTokens]는 호출 자체의 과금 단위다. */
data class EmotionExtractionOutput(
    val emotion: EmotionType,
    val usedTokens: Int,
    val cachedTokens: Int,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
)
