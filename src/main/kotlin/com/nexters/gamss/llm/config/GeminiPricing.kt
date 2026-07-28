package com.nexters.gamss.llm.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 모델별 토큰 요금표(USD, 100만 토큰당). Gemini는 요금 조회 API를 제공하지 않으므로 문서 기준으로
 * config에 둔다. 백오피스에서 모델을 바꾸면 각 생성 로그의 model 로 이 표를 조회해 자동으로 맞는 단가를
 * 쓴다 — 새 모델을 쓰면 여기 항목만 추가하면 된다.
 */
@ConfigurationProperties(prefix = "gemini.pricing")
data class GeminiPricing(
    /** 모델명 → 단가. 표에 없는 모델은 비용 계산에서 0으로 처리한다(요금 미등록). */
    val models: Map<String, ModelPricing> = emptyMap(),
) {
    /** [model]의 이번 생성 예상 비용(USD). 등록되지 않은 모델이면 0. */
    fun costUsd(
        model: String,
        inputTokens: Int,
        cachedTokens: Int,
        outputTokens: Int,
    ): Double = models[model]?.costUsd(inputTokens, cachedTokens, outputTokens) ?: 0.0
}
