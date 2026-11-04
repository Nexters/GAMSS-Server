package com.nexters.gamss.payment.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerifiedPurchaseTest {
    /**
     * 여기서 막지 않아도 지급은 상품 조회에서 실패한다. 다만 "판매 중이 아닌 상품" ERROR 로 남아, 검증 코드의
     * 버그가 스토어 상품 설정 문제처럼 보인다.
     */
    @Test
    fun `상품 ID 가 비어 있으면 받지 않는다`() {
        assertFailsWith<IllegalArgumentException> { purchase(productId = "") }
        assertFailsWith<IllegalArgumentException> { purchase(productId = " ") }
    }

    @Test
    fun `구매 수량은 양수만 받는다`() {
        assertFailsWith<IllegalArgumentException> { purchase(quantity = 0) }
        assertFailsWith<IllegalArgumentException> { purchase(quantity = -1) }
        assertEquals(1, purchase(quantity = 1).quantity)
    }

    private fun purchase(
        productId: String = "token_300000",
        quantity: Int = 1,
    ) = VerifiedPurchase(
        platform = PaymentPlatform.APPLE,
        storeTransactionKey = StoreTransactionKey("2000000123456789"),
        accountToken = null,
        productId = productId,
        quantity = quantity,
        environment = PaymentEnvironment.PRODUCTION,
        purchasedAt = Instant.parse("2026-11-04T00:00:00Z"),
    )
}
