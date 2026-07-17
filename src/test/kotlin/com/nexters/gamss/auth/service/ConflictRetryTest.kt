package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.oauth.OAuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConflictRetryTest {
    private val conflictRetry = ConflictRetry()

    private fun conflict() = ConcurrentRegistrationException(OAuthProvider.GOOGLE, "sub")

    @Test
    fun `성공하면 그대로 반환하고 한 번만 실행한다`() {
        var calls = 0

        val result =
            conflictRetry.execute {
                calls++
                "ok"
            }

        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `경합 예외가 나면 다시 실행해 성공한다`() {
        var calls = 0

        val result =
            conflictRetry.execute {
                calls++
                if (calls < 2) throw conflict()
                "ok"
            }

        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun `최대 시도를 넘겨도 경합이 계속되면 예외를 전파한다`() {
        var calls = 0

        assertFailsWith<ConcurrentRegistrationException> {
            conflictRetry.execute {
                calls++
                throw conflict()
            }
        }

        assertEquals(3, calls)
    }

    @Test
    fun `경합이 아닌 예외는 재시도하지 않고 전파한다`() {
        var calls = 0

        assertFailsWith<IllegalStateException> {
            conflictRetry.execute {
                calls++
                throw IllegalStateException("boom")
            }
        }

        assertEquals(1, calls)
    }
}
