package com.nexters.gamss.payment.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TokenProductTest {
    @Test
    fun `수량만큼 지급량을 곱한다`() {
        assertEquals(900_000, product(300_000).tokensFor(3))
    }

    @Test
    fun `수량이 양수가 아니면 지급하지 않는다`() {
        assertFailsWith<IllegalArgumentException> { product(300_000).tokensFor(0) }
    }

    @Test
    fun `곱이 넘치면 잘못된 값으로 지급하지 않고 예외로 끊는다`() {
        assertFailsWith<ArithmeticException> { product(Long.MAX_VALUE).tokensFor(2) }
    }

    private fun product(amount: Long) =
        TokenProduct(
            productId = "token_test",
            tokenAmount = amount,
            active = true,
            updatedAt = Instant.parse("2026-11-04T00:00:00Z"),
        )
}
