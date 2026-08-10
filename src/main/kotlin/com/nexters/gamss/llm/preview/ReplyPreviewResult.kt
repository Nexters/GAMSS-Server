package com.nexters.gamss.llm.preview

import com.nexters.gamss.emotion.domain.EmotionType

/** 답장 미리보기 결과. [PromptPreviewResult]와 같은 이유로 실패도 오류 필드에 담는다. */
data class ReplyPreviewResult(
    val model: String,
    val systemPrompt: String,
    val userContent: String,
    val character: EmotionType,
    val replyText: String?,
    val validationError: String?,
    val generationError: String?,
    val usedTokens: Int,
    val cachedTokens: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val estimatedCostUsd: Double,
    val latencyMs: Long,
)
