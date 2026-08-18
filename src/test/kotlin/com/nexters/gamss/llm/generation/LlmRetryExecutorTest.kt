package com.nexters.gamss.llm.generation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class LlmRetryExecutorTest {
    private val sleeper = RecordingSleeper()
    private val executor = LlmRetryExecutor(sleeper)

    @Test
    fun `1차에 실패해도 2차에 성공하면 결과를 돌려주고 소진 콜백은 부르지 않는다`() {
        val attempts = mutableListOf<Int>()
        val failedAttempts = mutableListOf<Int>()
        var exhausted = false

        val result =
            executor.execute(
                retryOn = RetryableFailure::class,
                maxAttempts = 2,
                onAttemptFailure = { attempt, _ -> failedAttempts += attempt },
                onExhausted = { _, _ -> exhausted = true },
            ) { attempt ->
                attempts += attempt
                if (attempt == 1) throw RetryableFailure("1차 실패")
                "성공"
            }

        assertEquals("성공", result)
        // 시도 번호는 0이 아니라 1부터 넘어와야 한다 — 그대로 GenerationLog.attemptCount가 된다.
        assertEquals(listOf(1, 2), attempts)
        assertEquals(listOf(1), failedAttempts)
        assertFalse(exhausted)
    }

    @Test
    fun `모든 시도가 실패하면 마지막 예외를 소진 콜백에 넘긴 뒤 그대로 던진다`() {
        val failedAttempts = mutableListOf<Int>()
        val lastFailure = RetryableFailure("2차 실패")
        var exhaustedAttempt: Int? = null
        var exhaustedError: RetryableFailure? = null

        val thrown =
            assertFailsWith<RetryableFailure> {
                executor.execute<String, RetryableFailure>(
                    retryOn = RetryableFailure::class,
                    maxAttempts = 2,
                    onAttemptFailure = { attempt, _ -> failedAttempts += attempt },
                    onExhausted = { attempt, e ->
                        exhaustedAttempt = attempt
                        exhaustedError = e
                    },
                ) { attempt ->
                    throw if (attempt == 1) RetryableFailure("1차 실패") else lastFailure
                }
            }

        // 마지막 시도의 토큰도 합산 대상이라, 시도 실패 콜백은 마지막 시도에서도 불려야 한다.
        assertEquals(listOf(1, 2), failedAttempts)
        assertEquals(2, exhaustedAttempt)
        assertEquals(lastFailure, exhaustedError)
        assertEquals(lastFailure, thrown)
    }

    @Test
    fun `재시도 대상이 아닌 예외는 재시도하지 않고 즉시 던진다`() {
        val attempts = mutableListOf<Int>()
        val permanentFailure = PermanentFailure("설정 오류")
        var nonRetryableAttempt: Int? = null

        val thrown =
            assertFailsWith<PermanentFailure> {
                executor.execute<String, RetryableFailure>(
                    retryOn = RetryableFailure::class,
                    maxAttempts = 3,
                    onNonRetryable = { attempt, _ -> nonRetryableAttempt = attempt },
                    onExhausted = { _, _ -> fail("재시도 대상이 아닌 예외에는 소진 콜백이 불리면 안 된다") },
                ) { attempt ->
                    attempts += attempt
                    throw permanentFailure
                }
            }

        assertEquals(listOf(1), attempts)
        assertEquals(1, nonRetryableAttempt)
        assertEquals(permanentFailure, thrown)
    }

    @Test
    fun `재시도 대상 예외의 하위 타입도 재시도한다`() {
        val attempts = mutableListOf<Int>()

        val result =
            executor.execute(retryOn = RetryableFailure::class, maxAttempts = 2) { attempt ->
                attempts += attempt
                if (attempt == 1) throw RetryableSubFailure("하위 타입 실패")
                "성공"
            }

        assertEquals("성공", result)
        assertEquals(listOf(1, 2), attempts)
    }

    @Test
    fun `maxAttempts가 1이면 재시도하지 않고 곧바로 소진 처리한다`() {
        val attempts = mutableListOf<Int>()
        var exhaustedAttempt: Int? = null

        assertFailsWith<RetryableFailure> {
            executor.execute<String, RetryableFailure>(
                retryOn = RetryableFailure::class,
                maxAttempts = 1,
                backoff = BackoffPolicy.fixed(500L),
                onExhausted = { attempt, _ -> exhaustedAttempt = attempt },
            ) { attempt ->
                attempts += attempt
                throw RetryableFailure("실패")
            }
        }

        assertEquals(listOf(1), attempts)
        assertEquals(1, exhaustedAttempt)
        // 재시도가 없으면 백오프를 설정해도 잘 이유가 없다.
        assertTrue(sleeper.sleptMillis.isEmpty())
    }

    @Test
    fun `백오프가 0이면 대기하지 않는다`() {
        assertFailsWith<RetryableFailure> {
            executor.execute<String, RetryableFailure>(
                retryOn = RetryableFailure::class,
                maxAttempts = 3,
                backoff = BackoffPolicy.fixed(0L),
            ) { throw RetryableFailure("실패") }
        }

        assertTrue(sleeper.sleptMillis.isEmpty())
    }

    @Test
    fun `백오프가 있으면 시도 사이에만 대기하고 마지막 시도 뒤에는 대기하지 않는다`() {
        assertFailsWith<RetryableFailure> {
            executor.execute<String, RetryableFailure>(
                retryOn = RetryableFailure::class,
                maxAttempts = 3,
                backoff = BackoffPolicy.fixed(500L),
            ) { throw RetryableFailure("실패") }
        }

        // 3회 시도 = 시도 사이 간격 2번.
        assertEquals(listOf(500L, 500L), sleeper.sleptMillis)
    }

    @Test
    fun `백오프 정책은 시도마다 다시 호출된다`() {
        // 시도 번호에 따라 값이 달라지는 정책 — 루프 밖에서 한 번만 계산하면 이 기대값이 깨진다.
        val perAttempt = BackoffPolicy { attempt, _ -> 100L * attempt }

        assertFailsWith<RetryableFailure> {
            executor.execute<String, RetryableFailure>(
                retryOn = RetryableFailure::class,
                maxAttempts = 3,
                backoff = perAttempt,
            ) { throw RetryableFailure("실패") }
        }

        assertEquals(listOf(100L, 200L), sleeper.sleptMillis)
    }

    @Test
    fun `백오프 정책에 그 시도를 실패시킨 예외가 전달된다`() {
        // #162는 이 예외로 429·검증 실패를 갈라 대기 간격을 정한다. 매번 그 시도의 예외여야 한다.
        val seenMessages = mutableListOf<String?>()
        val recording =
            BackoffPolicy { _, e ->
                seenMessages += e.message
                0L
            }

        assertFailsWith<RetryableFailure> {
            executor.execute<String, RetryableFailure>(
                retryOn = RetryableFailure::class,
                maxAttempts = 3,
                backoff = recording,
            ) { attempt -> throw RetryableFailure("${attempt}차 실패") }
        }

        // 마지막 시도 뒤에는 대기하지 않으므로 2건이다.
        assertEquals(listOf<String?>("1차 실패", "2차 실패"), seenMessages)
    }

    @Test
    fun `maxAttempts가 1보다 작으면 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            executor.execute(retryOn = RetryableFailure::class, maxAttempts = 0) { "성공" }
        }
    }
}

/** 실제로 자지 않고 요청받은 대기 시간만 기록한다. */
private class RecordingSleeper : RetrySleeper {
    val sleptMillis = mutableListOf<Long>()

    override fun sleep(millis: Long) {
        sleptMillis += millis
    }
}

private open class RetryableFailure(
    message: String,
) : RuntimeException(message)

private class RetryableSubFailure(
    message: String,
) : RetryableFailure(message)

private class PermanentFailure(
    message: String,
) : RuntimeException(message)
