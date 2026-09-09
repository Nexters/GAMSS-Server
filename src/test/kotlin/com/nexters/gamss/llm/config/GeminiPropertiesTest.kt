package com.nexters.gamss.llm.config

import com.nexters.gamss.llm.generation.LlmRetryPolicy
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeminiPropertiesTest {
    @Test
    fun `유효한 타임아웃으로 생성된다`() {
        val properties = GeminiProperties(model = "model", requestTimeout = Duration.ofSeconds(15))

        assertEquals(Duration.ofSeconds(15), properties.requestTimeout)
        assertEquals(15_000, properties.requestTimeoutMillis)
    }

    @Test
    fun `타임아웃이 0이면 예외`() {
        assertFailsWith<IllegalArgumentException> {
            GeminiProperties(model = "model", requestTimeout = Duration.ZERO)
        }
    }

    @Test
    fun `타임아웃이 음수면 예외`() {
        assertFailsWith<IllegalArgumentException> {
            GeminiProperties(model = "model", requestTimeout = Duration.ofSeconds(-1))
        }
    }

    @Test
    fun `타임아웃이 1밀리초 미만의 양수면 예외`() {
        assertFailsWith<IllegalArgumentException> {
            GeminiProperties(model = "model", requestTimeout = Duration.ofNanos(1))
        }
    }

    @Test
    fun `타임아웃이 1밀리초면 통과한다`() {
        val properties = GeminiProperties(model = "model", requestTimeout = Duration.ofMillis(1))

        assertEquals(1, properties.requestTimeoutMillis)
    }

    @Test
    fun `타임아웃이 Int 밀리초 범위를 넘으면 예외`() {
        assertFailsWith<IllegalArgumentException> {
            GeminiProperties(model = "model", requestTimeout = Duration.ofMillis(Int.MAX_VALUE.toLong() + 1))
        }
    }

    @Test
    fun `최악 재시도 시간이 예산과 같거나 크면 예외`() {
        val perAttemptMillis = smallestPerAttemptOverBudget()

        assertFailsWith<IllegalArgumentException> {
            GeminiProperties(model = "model", requestTimeout = Duration.ofMillis(perAttemptMillis))
        }
    }

    @Test
    fun `최악 재시도 시간이 예산보다 작으면 통과한다`() {
        val perAttemptMillis = smallestPerAttemptOverBudget() - STEP_MILLIS

        val properties =
            GeminiProperties(model = "model", requestTimeout = Duration.ofMillis(perAttemptMillis))

        assertEquals(perAttemptMillis.toInt(), properties.requestTimeoutMillis)
    }

    @Test
    fun `예산 검증이 요청당 호출 체인 수를 반영한다`() {
        // 카드 생성은 한 요청에서 감정 분류 → 한 줄 생성으로 LLM을 두 번 순차 호출한다. 체인을 하나로만
        // 세면 아래 값이 통과해버리지만, 실제 카드 경로는 그 두 배를 쓰므로 거부돼야 한다.
        val perAttemptMillis = 20_000L
        val singleChainMillis =
            perAttemptMillis * LlmRetryPolicy.MAX_ATTEMPTS + LlmRetryPolicy.worstCaseBackoffMillis()
        assertTrue(
            singleChainMillis < LlmRetryPolicy.TOTAL_TIMEOUT_BUDGET_MILLIS,
            "전제가 깨졌다 — 체인 1개 기준으로는 통과하는 값이어야 이 테스트가 의미를 갖는다",
        )

        assertFailsWith<IllegalArgumentException> {
            GeminiProperties(model = "model", requestTimeout = Duration.ofMillis(perAttemptMillis))
        }
    }

    @Test
    fun `운영 설정값 15초는 카드 경로까지 예산 안에 들어온다`() {
        // application.yml의 gemini.request-timeout과 같은 값. 이 테스트가 깨지면 배포가 뜨지 않는다.
        GeminiProperties(model = "model", requestTimeout = Duration.ofSeconds(15))
    }

    /** 예산을 처음으로 넘어서는 per-attempt 타임아웃. 상수가 바뀌어도 경계를 따라가도록 계산으로 구한다. */
    private fun smallestPerAttemptOverBudget(): Long =
        generateSequence(STEP_MILLIS) { it + STEP_MILLIS }
            .first {
                LlmRetryPolicy.worstCaseTotalMillis(it.toInt()) >= LlmRetryPolicy.TOTAL_TIMEOUT_BUDGET_MILLIS
            }

    companion object {
        private const val STEP_MILLIS = 1_000L
    }
}
