package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.domain.PaymentEnvironment
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.support.FakeAppleTransactionDecoder
import com.nexters.gamss.support.FakeAppleTransactionDecoder.Companion.transaction
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ApplePurchaseVerifierTest {
    private val decoder = FakeAppleTransactionDecoder()
    private val verifier = ApplePurchaseVerifier(decoder)

    @Test
    fun `소모성 거래는 transactionId 를 거래 키로 삼아 지급 입력을 만든다`() {
        decoder.stub(JWS, transaction(accountToken = ACCOUNT_TOKEN, environment = PaymentEnvironment.SANDBOX))

        val purchase = verifier.verify(JWS)

        assertEquals(PaymentPlatform.APPLE, purchase.platform)
        assertEquals(StoreTransactionKey("2000000123456789"), purchase.storeTransactionKey)
        assertEquals(ACCOUNT_TOKEN, purchase.accountToken)
        assertEquals("token_300000", purchase.productId)
        assertEquals(1, purchase.quantity)
        assertEquals(PaymentEnvironment.SANDBOX, purchase.environment)
        assertEquals(Instant.parse("2026-11-06T00:00:00Z"), purchase.purchasedAt)
        assertNull(purchase.storeOrderId)
        assertNull(purchase.storePurchaseToken)
    }

    @Test
    fun `계정 토큰이 없어도 여기서는 거부하지 않고 지급이 판정하게 넘긴다`() {
        decoder.stub(JWS, transaction(accountToken = null))

        assertNull(verifier.verify(JWS).accountToken)
    }

    @Test
    fun `여러 개를 산 거래는 그 수량을 그대로 넘긴다`() {
        listOf(2, 10).forEach { quantity ->
            decoder.stub(JWS, transaction(accountToken = ACCOUNT_TOKEN, quantity = quantity))

            assertEquals(quantity, verifier.verify(JWS).quantity)
        }
    }

    @Test
    fun `수량이 없으면 1 로 본다`() {
        decoder.stub(JWS, transaction(accountToken = ACCOUNT_TOKEN, quantity = null))

        assertEquals(1, verifier.verify(JWS).quantity)
    }

    @Test
    fun `Apple 상한을 벗어난 수량은 거부한다`() {
        listOf(0, -1, 11).forEach { quantity ->
            decoder.stub(JWS, transaction(accountToken = ACCOUNT_TOKEN, quantity = quantity))

            assertRejected(ErrorCode.PURCHASE_INVALID)
        }
    }

    @Test
    fun `환불되거나 취소된 거래는 지급하지 않는다`() {
        decoder.stub(JWS, transaction(accountToken = ACCOUNT_TOKEN, revocationDate = Instant.parse("2026-11-07T00:00:00Z")))

        assertRejected(ErrorCode.PURCHASE_CANCELLED)
    }

    @Test
    fun `소모성이 아닌 거래는 거부한다`() {
        listOf("Non-Consumable", "Auto-Renewable Subscription", null).forEach { type ->
            decoder.stub(JWS, transaction(accountToken = ACCOUNT_TOKEN, type = type))

            assertRejected(ErrorCode.PURCHASE_INVALID)
        }
    }

    @Test
    fun `거래 ID 나 상품 ID 가 없으면 거부한다`() {
        decoder.stub(JWS, transaction(accountToken = ACCOUNT_TOKEN, transactionId = null))
        assertRejected(ErrorCode.PURCHASE_INVALID)

        decoder.stub(JWS, transaction(accountToken = ACCOUNT_TOKEN, productId = " "))
        assertRejected(ErrorCode.PURCHASE_INVALID)
    }

    @Test
    fun `구매 시각이 없으면 finish 되지 않도록 일시 장애로 응답한다`() {
        decoder.stub(JWS, transaction(accountToken = ACCOUNT_TOKEN, purchaseDate = null))

        assertRejected(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
    }

    @Test
    fun `서명 검증 실패는 그대로 올린다`() {
        assertRejected(ErrorCode.PURCHASE_INVALID)

        decoder.unavailable = true
        assertRejected(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
    }

    private fun assertRejected(errorCode: ErrorCode) {
        val exception = assertFailsWith<BusinessException> { verifier.verify(JWS) }
        assertEquals(errorCode, exception.errorCode)
    }

    private companion object {
        const val JWS = "signed-transaction"
        const val ACCOUNT_TOKEN = "7f1c2a4e-3b5d-4c6e-8f90-a1b2c3d4e5f6"
    }
}
