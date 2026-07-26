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
    init {
        require(requestTimeout > Duration.ZERO) {
            "gemini.request-timeout은 0보다 커야 합니다: $requestTimeout"
        }
        // google-genai:1.51.0의 HttpOptions.timeout()은 Integer 밀리초를 받는다(GeminiCommentGenerator가
        // toMillis().toInt()로 변환). 이 범위를 넘으면 오버플로우로 음수가 되어 요청이 예외로 실패하거나,
        // 우연히 0이 되어 타임아웃이 없는 것처럼 동작해 이 프로퍼티의 목적을 조용히 무력화할 수 있다.
        require(requestTimeout.toMillis() <= Int.MAX_VALUE) {
            "gemini.request-timeout이 너무 큽니다(최대 ${Int.MAX_VALUE}ms=약 24.8일): $requestTimeout"
        }
    }
}
