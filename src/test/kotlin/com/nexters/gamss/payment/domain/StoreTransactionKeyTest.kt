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

    /**
     * 거래 키 컬럼은 `utf8mb4_0900_bin`(NO PAD)이라 뒤 공백까지 다른 값으로 본다. 공백이 섞인 키를 받으면
     * 같은 거래가 공백이 있는 키와 없는 키로 두 번 저장되고, 유니크 제약을 지나 두 번 지급된다.
     */
    @Test
    fun `공백이 섞인 거래 키는 받지 않는다`() {
        listOf("2000000123456789 ", " 2000000123456789", "2000000 123456789", "2000000123456789\t").forEach { key ->
            assertFailsWith<IllegalArgumentException>(key) { StoreTransactionKey(key) }
        }
    }

    @Test
    fun `64자 거래 키는 받고 원래 값 그대로 문자열이 된다`() {
        val key = "f".repeat(StoreTransactionKey.MAX_LENGTH)

        assertEquals(key, StoreTransactionKey(key).toString())
    }

    /**
     * 앱 요청, 웹훅, 대사 배치가 같은 구매를 같은 거래로 묶으려면 해시가 정확히 같아야 한다. 알려진
     * 입력의 SHA-256 을 고정해, 인코딩이나 대소문자가 바뀌면 바로 드러나게 한다.
     */
    @Test
    fun `Google 거래 키는 purchaseToken 의 SHA-256 소문자 hex 64자다`() {
        val key = StoreTransactionKey.ofGooglePurchaseToken("abc")

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", key.value)
        assertEquals(StoreTransactionKey.MAX_LENGTH, key.value.length)
        assertFailsWith<IllegalArgumentException> { StoreTransactionKey.ofGooglePurchaseToken(" ") }
    }
}
