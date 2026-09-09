package com.nexters.gamss.llm.config

import com.nexters.gamss.llm.generation.LlmRetryPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 호출 경로와 무관한 공용 설정. 경로별 인증 값은 각 경로의 프로퍼티가 갖는다. */
@ConfigurationProperties(prefix = "gemini")
data class GeminiProperties(
    val model: String,
    /** LLM 호출 1회당 타임아웃. 저장+생성을 한 요청으로 묶은 뒤 nginx proxy_read_timeout(120s)이
     * 하드 리밋이 되므로, [LlmRetryPolicy]의 전체 재시도 시간을 그보다 짧게 제한한다. */
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
        val totalTimeoutMillis = LlmRetryPolicy.totalTimeoutMillis(requestTimeoutMillis)
        require(totalTimeoutMillis < LlmRetryPolicy.TOTAL_TIMEOUT_BUDGET_MILLIS) {
            "gemini.request-timeout의 최대 재시도 시간이 " +
                "${LlmRetryPolicy.TOTAL_TIMEOUT_BUDGET_MILLIS}ms 미만이어야 합니다 " +
                "(attempts=${LlmRetryPolicy.MAX_ATTEMPTS}, " +
                "backoff=${LlmRetryPolicy.RETRY_BACKOFF_MILLIS}ms): $requestTimeout"
        }
    }
}
