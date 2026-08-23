package com.nexters.gamss.llm.config

import com.nexters.gamss.llm.generation.LlmRetryPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "gemini")
data class GeminiProperties(
    val apiKey: String,
    val model: String,
    /**
     * LLM 호출 **1회당** 타임아웃. "보통 얼마나 걸리나"가 아니라 "언제 포기하나"를 정하는 값이라,
     * 관측된 지연(prod p99 약 2s, 최댓값 약 5s)보다 넉넉하되 매달린 호출이 요청 스레드를 오래 붙잡지
     * 않을 만큼으로 잡는다. 재시도·호출 체인까지 곱한 최악 시간은 [LlmRetryPolicy]의 예산 안에 있어야 한다.
     */
    val requestTimeout: Duration,
) {
    /**
     * google-genai:1.51.0의 HttpOptions.timeout()에 그대로 전달할 검증된 정수 밀리초 값이다.
     * 1ms 미만의 양수 Duration도 toMillis()에서 0으로 잘리므로 유효하지 않은 설정으로 거부한다.
     */
    val requestTimeoutMillis: Int =
        requestTimeout
            .toMillis()
            .also { millis ->
                require(millis in 1..Int.MAX_VALUE.toLong()) {
                    "gemini.request-timeout은 1..${Int.MAX_VALUE}ms 범위여야 합니다: $requestTimeout"
                }
            }.toInt()

    init {
        val worstCaseMillis = LlmRetryPolicy.worstCaseTotalMillis(requestTimeoutMillis)
        require(worstCaseMillis < LlmRetryPolicy.TOTAL_TIMEOUT_BUDGET_MILLIS) {
            "gemini.request-timeout의 최악 재시도 시간이 " +
                "${LlmRetryPolicy.TOTAL_TIMEOUT_BUDGET_MILLIS}ms 미만이어야 합니다 " +
                "(attempts=${LlmRetryPolicy.MAX_ATTEMPTS}, " +
                "chains=${LlmRetryPolicy.MAX_CALL_CHAINS_PER_REQUEST}, " +
                "backoff=${LlmRetryPolicy.worstCaseBackoffMillis()}ms): $requestTimeout"
        }
    }
}
