package com.nexters.gamss.llm.generation

import com.nexters.gamss.llm.error.CardGenerationFailedException
import com.nexters.gamss.llm.error.CommentGenerationFailedException

/**
 * 재시도(다중 LLM 호출) 동안 시도별 토큰을 합산한다. 실패한 시도도 호출이 됐다면 실제로는 과금되므로,
 * 성공한 시도만이 아니라 모든 시도의 토큰을 더해 최종 생성 로그에 누락 없이 담기 위한 누산기다
 * (성공/실패 각각 마지막 한 시도만 기록하던 과소 집계를 막는다).
 *
 * 댓글·답글과 카드는 결과·예외 타입이 서로 다를 뿐 세는 방식이 같아, 타입별 오버로드로 받는다.
 */
class TokenUsageAccumulator {
    var used: Int = 0
        private set
    var cached: Int = 0
        private set
    var input: Int = 0
        private set
    var output: Int = 0
        private set

    fun add(o: CommentGenerationOutput) = accumulate(o.usedTokens, o.cachedTokens, o.inputTokens, o.outputTokens)

    fun add(o: ReplyGenerationOutput) = accumulate(o.usedTokens, o.cachedTokens, o.inputTokens, o.outputTokens)

    fun add(o: EmotionExtractionOutput) = accumulate(o.usedTokens, o.cachedTokens, o.inputTokens, o.outputTokens)

    fun add(o: CardMessageOutput) = accumulate(o.usedTokens, o.cachedTokens, o.inputTokens, o.outputTokens)

    /** 실패한 시도: 호출은 됐지만 검증/파싱 실패라 예외에 실린 토큰을 더한다(호출 자체 실패면 null → 0). */
    fun addFailed(e: CommentGenerationFailedException) =
        accumulate(e.usedTokens ?: 0, e.cachedTokens ?: 0, e.inputTokens ?: 0, e.outputTokens ?: 0)

    /** 실패한 시도: 호출은 됐지만 검증/파싱 실패라 예외에 실린 토큰을 더한다(호출 자체 실패면 null → 0). */
    fun addFailed(e: CardGenerationFailedException) =
        accumulate(e.usedTokens ?: 0, e.cachedTokens ?: 0, e.inputTokens ?: 0, e.outputTokens ?: 0)

    private fun accumulate(
        usedTokens: Int,
        cachedTokens: Int,
        inputTokens: Int,
        outputTokens: Int,
    ) {
        used += usedTokens
        cached += cachedTokens
        input += inputTokens
        output += outputTokens
    }
}
