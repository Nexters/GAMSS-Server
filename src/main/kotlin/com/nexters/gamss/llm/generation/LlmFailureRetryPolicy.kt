package com.nexters.gamss.llm.generation

import com.nexters.gamss.llm.error.LlmFailure
import com.nexters.gamss.llm.error.LlmFailureKind

/**
 * 실패 종류([LlmFailureKind])로 **"다시 부를지"와 "얼마나 기다릴지"를 함께** 답하는 재시도 정책이다.
 * 둘 다 같은 정보로 갈리므로 한 자리에 두었다 — 나뉘어 있으면 종류를 하나 추가할 때 한쪽만 고치고
 * 지나치기 쉽다.
 *
 * | 실패 | 재시도 | 대기 |
 * |---|---|---|
 * | 네트워크·IO·5xx([LlmFailureKind.CALL]) | O | 지수 0.5s → 1s |
 * | 429([LlmFailureKind.RATE_LIMITED]) | O | 고정 2s |
 * | 400·401·403([LlmFailureKind.PERMANENT]) | **X** | — |
 * | 응답 검증 실패([LlmFailureKind.VALIDATION]) | O | **없음**(즉시) |
 *
 * 실패 종류를 모르는 예외(= [LlmFailure]가 아닌 예외)는 재시도 대상으로 보고 지수 백오프를 준다 —
 * 종류를 못 읽었다는 이유로 재시도를 포기하지 않기 위해서다.
 */
internal object LlmFailureRetryPolicy : BackoffPolicy {
    /** 다시 불러도 같은 답이 올 실패(인증·권한·요청 형식)만 걸러낸다. */
    fun shouldRetry(error: Exception): Boolean = kindOf(error) != LlmFailureKind.PERMANENT

    override fun delayMillisFor(
        attempt: Int,
        error: Exception,
    ): Long =
        when (kindOf(error)) {
            // Gemini는 멀쩡하고 형식만 틀렸다. 기다려도 나아질 것이 없다.
            LlmFailureKind.VALIDATION -> 0L

            LlmFailureKind.RATE_LIMITED -> LlmRetryPolicy.RATE_LIMIT_BACKOFF_MILLIS

            else -> LlmRetryPolicy.backoffMillisFor(attempt)
        }

    private fun kindOf(error: Exception): LlmFailureKind? = (error as? LlmFailure)?.kind
}
