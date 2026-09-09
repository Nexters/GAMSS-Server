package com.nexters.gamss.llm.generation

/**
 * 동기 LLM 생성 요청의 재시도 및 전체 타임아웃 정책이다. 실제 판정은
 * [LlmFailureRetryPolicy]가 하고, 여기에는 그 정책이 쓰는 수치와 예산 계산만 둔다.
 *
 * ### 전체 시간이 넘으면 안 되는 두 개의 벽
 *
 * 1. **nginx `proxy_read_timeout`(120s)** — 저장+생성을 한 요청으로 묶었으므로 이게 하드 리밋이다.
 *    [TOTAL_TIMEOUT_BUDGET_MILLIS]가 그 값이고, [worstCaseTotalMillis]가 이보다 짧아야 한다.
 * 2. **`conversation.pending-generation-timeout`(3분)** — 이걸 넘기면 정리 스케줄러가 아직 진행 중인
 *    PENDING을 되돌려, 응답을 받고 토큰까지 낸 생성이 저장 단계에서 통째로 롤백된다
 *    (`CommentPersistenceService`의 `check(updated == 1)`). 1번 예산이 3분보다 훨씬 짧아 자동으로 지켜지지만,
 *    예산을 올릴 때는 이 벽을 함께 봐야 한다.
 */
internal object LlmRetryPolicy {
    const val MAX_ATTEMPTS = 3

    /** 네트워크·5xx 실패의 첫 대기. 시도마다 2배로 늘어난다([backoffMillisFor]). */
    const val INITIAL_BACKOFF_MILLIS = 500L

    /**
     * 429 쿼터 초과의 고정 대기. 지수 백오프의 첫 간격(0.5s)으로는 분당 쿼터가 회복되지 않아 그대로 또
     * 429가 난다. SDK가 응답 헤더를 노출하지 않아 `Retry-After`를 읽을 수 없어 고정값으로 둔다.
     */
    const val RATE_LIMIT_BACKOFF_MILLIS = 2_000L

    /**
     * 한 요청이 순차로 부르는 LLM 호출 체인의 최대 개수. 카드 생성은 클라이언트가 `emotion`을 보내지
     * 않으면 감정 분류 → 한 줄 생성으로 **두 번** 부른다. 이 값이 없으면 "한 요청 = 체인 1개"를 가정한
     * 예산 검증이 카드 경로에서 두 배로 빗나간다.
     */
    const val MAX_CALL_CHAINS_PER_REQUEST = 2

    /**
     * nginx `proxy_read_timeout`과 같은 하드 리밋. 검증·저장·응답 처리 몫을 따로 떼어두지는 않는다 —
     * 그 시간은 수십 ms 수준이라, 마진을 잡아 설정 여지를 좁히는 것보다 리밋을 그대로 두는 편이 낫다.
     */
    const val TOTAL_TIMEOUT_BUDGET_MILLIS = 120_000L

    /** [attempt]번째 시도가 실패한 뒤의 지수 백오프: 0.5s → 1s → 2s … */
    fun backoffMillisFor(attempt: Int): Long = INITIAL_BACKOFF_MILLIS shl (attempt - 1)

    /**
     * 한 요청이 최악의 경우 LLM에 쓰는 시간. 매 시도가 타임아웃까지 매달리고, 대기는 가장 긴 종류로
     * 나며, 체인이 최대로 이어지는 경우다.
     */
    fun worstCaseTotalMillis(perAttemptTimeoutMillis: Int): Long =
        (perAttemptTimeoutMillis.toLong() * MAX_ATTEMPTS + worstCaseBackoffMillis()) * MAX_CALL_CHAINS_PER_REQUEST

    /** 체인 하나가 대기에 쓰는 최악 시간. 429 고정 대기와 지수 백오프 중 긴 쪽이다. */
    fun worstCaseBackoffMillis(): Long {
        val gaps = MAX_ATTEMPTS - 1
        val exponential = (1..gaps).sumOf { backoffMillisFor(it) }
        return maxOf(RATE_LIMIT_BACKOFF_MILLIS * gaps, exponential)
    }
}
