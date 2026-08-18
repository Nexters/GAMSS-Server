package com.nexters.gamss.llm.generation

import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * "LLM 호출 + 응답 검증"을 한 시도 단위로 묶어 반복하는 재시도 루프다. 댓글·답글·카드 생성 경로가
 * 각자 들고 있던 같은 모양의 루프를 한 곳으로 모은 것이다.
 *
 * **시도별 토큰 합산과 생성 로그 기록은 여기 들어오지 않는다.** 검증에 실패한 시도도 호출은 됐으니
 * 과금되기 때문에, 매 시도의 토큰을 호출자가 직접 누산해야 한다(과거 마지막 한 시도만 기록해
 * 과소 집계한 버그가 있던 자리다). 그래서 [call]에 시도 번호를 넘겨주고, 실패 시점마다 콜백을
 * 불러 호출자가 끼어들 자리를 만든다 — 라이브러리의 선언적 재시도(`@Retryable` 등)를 쓰지 않는
 * 이유가 이것이다.
 */
@Component
class LlmRetryExecutor(
    private val sleeper: RetrySleeper = RetrySleeper.THREAD,
) {
    /**
     * [call]을 최대 [maxAttempts]회 시도하고, 성공한 결과를 돌려준다.
     *
     * [retryOn] 타입의 예외만 재시도한다. 그 외 예외는 다시 불러도 결과가 같을 것이라 보고 즉시
     * 중단한다. 어느 쪽이든 마지막에는 원래 예외를 그대로 던진다 — 호출자가 예외 타입으로 분기하는
     * 기존 동작을 바꾸지 않기 위해서다.
     *
     * @param call 한 번의 시도. 시도 번호(1부터)를 받는다. LLM 호출·검증·성공 로그가 여기 들어간다.
     * @param onAttemptFailure 재시도 대상 실패마다. **마지막 시도에서도 불린다** — 그 시도의 토큰도
     *   합산돼야 하기 때문이다.
     * @param onNonRetryable 재시도 대상이 아닌 예외로 중단할 때. 던지기 직전에 불린다.
     * @param onExhausted 모든 시도를 소진했을 때. 마지막 실패 예외를 들고 던지기 직전에 불린다.
     */
    fun <T, E : Exception> execute(
        retryOn: KClass<E>,
        maxAttempts: Int = LlmRetryPolicy.MAX_ATTEMPTS,
        backoff: BackoffPolicy = BackoffPolicy.fixed(LlmRetryPolicy.RETRY_BACKOFF_MILLIS),
        onAttemptFailure: (attempt: Int, e: E) -> Unit = { _, _ -> },
        onNonRetryable: (attempt: Int, e: Exception) -> Unit = { _, _ -> },
        onExhausted: (attempt: Int, e: E) -> Unit = { _, _ -> },
        call: (attempt: Int) -> T,
    ): T {
        require(maxAttempts >= 1) { "maxAttempts는 1 이상이어야 합니다: $maxAttempts" }

        var lastError: E? = null
        repeat(maxAttempts) { index ->
            val attempt = index + 1
            try {
                return call(attempt)
            } catch (e: Exception) {
                if (!retryOn.isInstance(e)) {
                    onNonRetryable(attempt, e)
                    throw e
                }
                @Suppress("UNCHECKED_CAST")
                val retryable = e as E
                lastError = retryable
                onAttemptFailure(attempt, retryable)
                // 마지막 시도 뒤에는 잘 이유가 없다. 정책이 0을 주면(현재 기본값) 아예 재우지 않는다.
                if (attempt < maxAttempts) {
                    val delayMillis = backoff.delayMillisFor(attempt, retryable)
                    if (delayMillis > 0) {
                        sleeper.sleep(delayMillis)
                    }
                }
            }
        }

        val error = checkNotNull(lastError)
        onExhausted(maxAttempts, error)
        throw error
    }
}
