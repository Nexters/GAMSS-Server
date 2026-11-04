package com.nexters.gamss.payment.domain

import com.nexters.gamss.wallet.domain.LedgerRefId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentTest {
    @Test
    fun `같은 회수 목표를 두 번 맞추면 두 번째는 바뀐 양이 0 이다`() {
        val payment = payment(granted = 3_000)

        assertEquals(3_000, payment.refundTo(3_000))
        assertEquals(0, payment.refundTo(3_000))
        assertEquals(3_000, payment.refundedTokens)
    }

    @Test
    fun `환불 취소는 목표 0 으로 맞추고 음수 차이를 돌려준다`() {
        val payment = payment(granted = 3_000)
        payment.refundTo(3_000)

        assertEquals(-3_000, payment.refundTo(0))
        assertEquals(0, payment.refundedTokens)
    }

    @Test
    fun `부분 환불 뒤 전액 환불이 오면 남은 차이만 회수한다`() {
        val payment = payment(granted = 3_000)

        assertEquals(1_000, payment.refundTo(1_000))
        assertEquals(2_000, payment.refundTo(3_000))
    }

    @Test
    fun `회수 목표가 0 미만이거나 지급량을 넘으면 받지 않는다`() {
        val payment = payment(granted = 3_000)

        assertFailsWith<IllegalArgumentException> { payment.refundTo(-1) }
        assertFailsWith<IllegalArgumentException> { payment.refundTo(3_001) }
        assertEquals(0, payment.refundedTokens)
    }

    @Test
    fun `가장 긴 거래 키와 알림 ID 로 만든 멱등 키도 원장 길이 안에 들어간다`() {
        val payment = payment(granted = 1, key = "f".repeat(StoreTransactionKey.MAX_LENGTH), platform = PaymentPlatform.GOOGLE)
        val notificationUuid = "3f2b8c1e-7a4d-4e6b-9c0a-1d2e3f4a5b6c"

        LedgerRefId(payment.purchaseRefId())
        LedgerRefId(payment.refundRefId(notificationUuid))
    }

    @Test
    fun `Google 64자 거래 키와 함께 쓰는 환불 출처 ID 는 49자까지다`() {
        val payment = payment(granted = 1, key = "f".repeat(StoreTransactionKey.MAX_LENGTH), platform = PaymentPlatform.GOOGLE)

        LedgerRefId(payment.refundRefId("s".repeat(49)))
        assertFailsWith<IllegalArgumentException> { LedgerRefId(payment.refundRefId("s".repeat(50))) }
    }

    @Test
    fun `같은 거래인지는 주체 상품 수량이 모두 같을 때만 참이다`() {
        val payment = payment(granted = 3_000)

        assertTrue(payment.isSamePurchase(SUBJECT, "token_300000", 1))
        assertFalse(payment.isSamePurchase("b".repeat(64), "token_300000", 1))
        assertFalse(payment.isSamePurchase(SUBJECT, "token_500000", 1))
        assertFalse(payment.isSamePurchase(SUBJECT, "token_300000", 2))
    }

    private fun payment(
        granted: Long,
        key: String = "2000000123456789",
        platform: PaymentPlatform = PaymentPlatform.APPLE,
    ) = Payment(
        platform = platform,
        storeTransactionKey = key,
        storePurchaseToken = null,
        storeOrderId = null,
        subjectKey = SUBJECT,
        productId = "token_300000",
        quantity = 1,
        grantedTokens = granted,
        environment = PaymentEnvironment.PRODUCTION,
        purchasedAt = Instant.parse("2026-11-04T00:00:00Z"),
    )

    private companion object {
        val SUBJECT = "a".repeat(64)
    }
}
