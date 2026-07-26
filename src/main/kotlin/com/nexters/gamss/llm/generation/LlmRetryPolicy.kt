package com.nexters.gamss.llm.generation

/**
 * 동기 LLM 생성 요청의 재시도 및 전체 타임아웃 정책이다.
 *
 * 현재 재시도 사이에 별도 backoff는 없으므로 0ms이며, 전체 요청은 nginx의
 * proxy_read_timeout(120s)보다 짧아야 후속 검증·저장·응답 처리 시간을 확보할 수 있다.
 */
internal object LlmRetryPolicy {
    const val MAX_ATTEMPTS = 2
    const val RETRY_BACKOFF_MILLIS = 0L
    const val TOTAL_TIMEOUT_BUDGET_MILLIS = 120_000L

    fun totalTimeoutMillis(perAttemptTimeoutMillis: Int): Long =
        perAttemptTimeoutMillis.toLong() * MAX_ATTEMPTS +
            RETRY_BACKOFF_MILLIS * (MAX_ATTEMPTS - 1)
}
