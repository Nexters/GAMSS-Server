package com.nexters.gamss.llm.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "gemini")
data class GeminiProperties(
    val apiKey: String,
    val model: String,
    /** LLM 호출 1회당 타임아웃. 저장+생성을 한 요청으로 묶은 뒤 nginx proxy_read_timeout(120s)이
     * 하드 리밋이 되므로, 재시도 최대 2회를 감안해도 그 안에 들어오도록 명시적으로 제한한다. */
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
}
