package com.nexters.gamss.llm.generation

import com.nexters.gamss.llm.error.CardGenerationFailedException
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import com.nexters.gamss.llm.error.LlmFailureKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmFailureRetryPolicyTest {
    @Test
    fun `영구 실패는 재시도하지 않는다`() {
        assertFalse(LlmFailureRetryPolicy.shouldRetry(failure(LlmFailureKind.PERMANENT)))
    }

    @Test
    fun `나머지 실패는 모두 재시도한다`() {
        listOf(LlmFailureKind.CALL, LlmFailureKind.RATE_LIMITED, LlmFailureKind.VALIDATION).forEach { kind ->
            assertTrue(LlmFailureRetryPolicy.shouldRetry(failure(kind)), "kind=$kind")
        }
    }

    @Test
    fun `서킷이 열려 막힌 호출은 재시도하지 않는다`() {
        // 다시 불러도 SDK까지 가지 못하고 같은 자리에서 막힌다. 재시도는 사용자를 기다리게만 한다.
        assertFalse(LlmFailureRetryPolicy.shouldRetry(failure(LlmFailureKind.CIRCUIT_OPEN)))
    }

    @Test
    fun `실패 종류를 모르는 예외는 재시도 대상으로 본다`() {
        // 종류를 못 읽었다는 이유로 재시도를 포기하지 않는다.
        assertTrue(LlmFailureRetryPolicy.shouldRetry(IllegalStateException("종류 없음")))
    }

    @Test
    fun `검증 실패는 기다리지 않고 곧바로 다시 부른다`() {
        // Gemini는 멀쩡하고 형식만 틀렸다 — 기다려도 나아질 것이 없다.
        assertEquals(0L, LlmFailureRetryPolicy.delayMillisFor(1, failure(LlmFailureKind.VALIDATION)))
        assertEquals(0L, LlmFailureRetryPolicy.delayMillisFor(2, failure(LlmFailureKind.VALIDATION)))
    }

    @Test
    fun `429는 시도 번호와 무관하게 같은 간격으로 기다린다`() {
        // 지수 백오프의 첫 간격으로는 분당 쿼터가 회복되지 않는다.
        val expected = LlmRetryPolicy.RATE_LIMIT_BACKOFF_MILLIS

        assertEquals(expected, LlmFailureRetryPolicy.delayMillisFor(1, failure(LlmFailureKind.RATE_LIMITED)))
        assertEquals(expected, LlmFailureRetryPolicy.delayMillisFor(2, failure(LlmFailureKind.RATE_LIMITED)))
    }

    @Test
    fun `호출 실패는 시도마다 대기가 두 배로 늘어난다`() {
        assertEquals(500L, LlmFailureRetryPolicy.delayMillisFor(1, failure(LlmFailureKind.CALL)))
        assertEquals(1_000L, LlmFailureRetryPolicy.delayMillisFor(2, failure(LlmFailureKind.CALL)))
        assertEquals(2_000L, LlmFailureRetryPolicy.delayMillisFor(3, failure(LlmFailureKind.CALL)))
    }

    @Test
    fun `카드 계열 예외도 같은 방식으로 읽는다`() {
        val cardFailure = CardGenerationFailedException("카드 실패", kind = LlmFailureKind.PERMANENT)

        assertFalse(LlmFailureRetryPolicy.shouldRetry(cardFailure))
    }

    private fun failure(kind: LlmFailureKind) = CommentGenerationFailedException("실패", kind = kind)
}
