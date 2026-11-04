package com.nexters.gamss.payment.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StoreTransactionKeyTest {
    @Test
    fun `거래 키는 비어 있거나 64자를 넘거나 구분자를 담을 수 없다`() {
        assertFailsWith<IllegalArgumentException> { StoreTransactionKey(" ") }
        assertFailsWith<IllegalArgumentException> { StoreTransactionKey("f".repeat(StoreTransactionKey.MAX_LENGTH + 1)) }
        assertFailsWith<IllegalArgumentException> { StoreTransactionKey("tx:1") }
    }

    @Test
    fun `64자 거래 키는 받고 원래 값 그대로 문자열이 된다`() {
        val key = "f".repeat(StoreTransactionKey.MAX_LENGTH)

        assertEquals(key, StoreTransactionKey(key).toString())
    }
}
