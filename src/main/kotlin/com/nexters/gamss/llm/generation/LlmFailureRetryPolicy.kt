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
 * | 서킷 오픈([LlmFailureKind.CIRCUIT_OPEN]) | **X** | — |
 *
 * 실패 종류를 모르는 예외(= [LlmFailure]가 아닌 예외)는 재시도 대상으로 보고 지수 백오프를 준다 —
 * 종류를 못 읽었다는 이유로 재시도를 포기하지 않기 위해서다.
 */
internal object LlmFailureRetryPolicy : BackoffPolicy {
    /**
     * 다시 불러도 결과가 달라지지 않을 실패를 걸러낸다.
     *
     * 종류를 빠짐없이 적는 것은 새 종류가 늘 때 여기서 컴파일이 깨지게 하려는 것이다. `!=` 하나로
     * 두거나 `else`에 얹으면 재시도하면 안 되는 종류가 조용히 재시도 쪽으로 흘러간다.
     */
    fun shouldRetry(error: Exception): Boolean =
        when (kindOf(error)) {
            LlmFailureKind.CALL, LlmFailureKind.RATE_LIMITED, LlmFailureKind.VALIDATION -> true

            // 인증·권한·요청 형식은 사람이 설정을 고치기 전에는 같은 답이고, 서킷이 열린 동안은
            // 호출이 아예 나가지 않는다. 둘 다 다시 불러봐야 실패할 것을 알면서 기다리게 하는 셈이다.
            LlmFailureKind.PERMANENT, LlmFailureKind.CIRCUIT_OPEN -> false

            // 종류를 못 읽은 예외. 못 읽었다는 이유로 재시도를 포기하지는 않는다.
            null -> true
        }

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
