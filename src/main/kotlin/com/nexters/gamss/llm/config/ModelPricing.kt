package com.nexters.gamss.llm.config

/**
 * 모델 1종의 토큰 요금(USD, 100만 토큰당). 캐시 입력은 정상 입력가보다 할인된 별도 단가다.
 * 출력은 캐시되지 않는다. (Gemini는 요금 조회 API가 없어 문서 기준으로 config에 둔다.)
 */
data class ModelPricing(
    val inputPer1M: Double,
    val cachedInputPer1M: Double,
    val outputPer1M: Double,
) {
    /**
     * 생성 1회의 예상 비용(USD). [cachedTokens]는 [inputTokens]의 부분집합(캐시로 처리된 입력)이라,
     * 나머지 입력은 정상가·캐시분은 할인가·출력은 출력가로 계산한다.
     */
    fun costUsd(
        inputTokens: Int,
        cachedTokens: Int,
        outputTokens: Int,
    ): Double {
        val nonCachedInput = (inputTokens - cachedTokens).coerceAtLeast(0)
        return nonCachedInput / MILLION * inputPer1M +
            cachedTokens / MILLION * cachedInputPer1M +
            outputTokens / MILLION * outputPer1M
    }

    companion object {
        private const val MILLION = 1_000_000.0
    }
}
