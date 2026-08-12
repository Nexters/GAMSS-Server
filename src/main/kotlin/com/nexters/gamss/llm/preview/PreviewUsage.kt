package com.nexters.gamss.llm.preview

import com.nexters.gamss.llm.config.GeminiPricing
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import com.nexters.gamss.llm.generation.CommentGenerationOutput
import com.nexters.gamss.llm.generation.ReplyGenerationOutput

/**
 * 미리보기 호출 1회가 소비한 토큰·예상 비용. 성공 출력과 실패 예외 양쪽을 같은 규칙으로
 * 환산해, 비용 계산을 결과 조립부마다 반복하지 않고 한 곳에 모은다.
 */
data class PreviewUsage(
    val usedTokens: Int,
    val cachedTokens: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val estimatedCostUsd: Double,
) {
    companion object {
        fun of(
            pricing: GeminiPricing,
            model: String,
            output: CommentGenerationOutput,
        ): PreviewUsage = of(pricing, model, output.usedTokens, output.cachedTokens, output.inputTokens, output.outputTokens)

        fun of(
            pricing: GeminiPricing,
            model: String,
            output: ReplyGenerationOutput,
        ): PreviewUsage = of(pricing, model, output.usedTokens, output.cachedTokens, output.inputTokens, output.outputTokens)

        /** 실패 예외에는 과금 정보가 없을 수 있다(호출 자체 실패) - 그 경우 0으로 환산한다. */
        fun of(
            pricing: GeminiPricing,
            model: String,
            e: CommentGenerationFailedException,
        ): PreviewUsage = of(pricing, model, e.usedTokens ?: 0, e.cachedTokens ?: 0, e.inputTokens ?: 0, e.outputTokens ?: 0)

        private fun of(
            pricing: GeminiPricing,
            model: String,
            usedTokens: Int,
            cachedTokens: Int,
            inputTokens: Int,
            outputTokens: Int,
        ): PreviewUsage =
            PreviewUsage(
                usedTokens = usedTokens,
                cachedTokens = cachedTokens,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                estimatedCostUsd = pricing.costUsd(model, inputTokens, cachedTokens, outputTokens),
            )
    }
}
