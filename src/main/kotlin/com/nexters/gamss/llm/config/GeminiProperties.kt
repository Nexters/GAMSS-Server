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
)
