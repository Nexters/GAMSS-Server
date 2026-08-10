package com.nexters.gamss.llm.preview

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.parsing.CommentFeed

/**
 * 미리보기 결과. 생성이 실패해도 예외로 끝내지 않고 [generationError]에 담는다 - 실패 원인과
 * 이미 과금된 토큰을 보는 것 자체가 플레이그라운드의 목적이기 때문이다.
 */
data class PromptPreviewResult(
    val model: String,
    val systemPrompt: String,
    val userContent: String,
    val characters: List<EmotionType>,
    val tikitakaCount: Int,
    val eongttungTopic: String?,
    val feed: CommentFeed?,
    val validationError: String?,
    val generationError: String?,
    val usedTokens: Int,
    val cachedTokens: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val estimatedCostUsd: Double,
    val latencyMs: Long,
)
