package com.nexters.gamss.llm.config

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeminiPropertiesTest {
    @Test
    fun `유효한 타임아웃으로 생성된다`() {
        val properties = GeminiProperties(apiKey = "key", model = "model", requestTimeout = Duration.ofSeconds(30))

        assertEquals(Duration.ofSeconds(30), properties.requestTimeout)
        assertEquals(30_000, properties.requestTimeoutMillis)
    }

    @Test
    fun `타임아웃이 0이면 예외`() {
        assertFailsWith<IllegalArgumentException> {
            GeminiProperties(apiKey = "key", model = "model", requestTimeout = Duration.ZERO)
        }
    }

    @Test
    fun `타임아웃이 음수면 예외`() {
        assertFailsWith<IllegalArgumentException> {
            GeminiProperties(apiKey = "key", model = "model", requestTimeout = Duration.ofSeconds(-1))
        }
    }

    @Test
    fun `타임아웃이 1밀리초 미만의 양수면 예외`() {
        assertFailsWith<IllegalArgumentException> {
            GeminiProperties(apiKey = "key", model = "model", requestTimeout = Duration.ofNanos(1))
        }
    }

    @Test
    fun `타임아웃이 1밀리초면 통과한다`() {
        val properties = GeminiProperties(apiKey = "key", model = "model", requestTimeout = Duration.ofMillis(1))

        assertEquals(1, properties.requestTimeoutMillis)
    }

    @Test
    fun `타임아웃이 Int 밀리초 범위를 넘으면 예외`() {
        assertFailsWith<IllegalArgumentException> {
            GeminiProperties(apiKey = "key", model = "model", requestTimeout = Duration.ofMillis(Int.MAX_VALUE.toLong() + 1))
        }
    }

    @Test
    fun `타임아웃이 Int 밀리초 범위의 최댓값이면 통과한다`() {
        val properties =
            GeminiProperties(apiKey = "key", model = "model", requestTimeout = Duration.ofMillis(Int.MAX_VALUE.toLong()))

        assertEquals(Int.MAX_VALUE.toLong(), properties.requestTimeout.toMillis())
        assertEquals(Int.MAX_VALUE, properties.requestTimeoutMillis)
    }
}
