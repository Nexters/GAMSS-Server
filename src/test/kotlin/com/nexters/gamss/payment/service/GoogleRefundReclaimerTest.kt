package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.domain.Payment
import com.nexters.gamss.payment.domain.PaymentEnvironment
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.google.GoogleConsumptionState
import com.nexters.gamss.payment.google.GooglePlayPurchase
import com.nexters.gamss.payment.google.GooglePurchaseLineItem
import com.nexters.gamss.payment.repository.PaymentRepository
import com.nexters.gamss.support.FakeGooglePlayClient
import com.nexters.gamss.support.FakeGooglePlayClient.Companion.purchase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 회수 목표 계산. 부분 환불 경로는 지금 수량 1 만 지급해 운영에서는 돌지 않지만, 다중 수량을 켜는 순간 살아나므로
 * 여기서 고정해 둔다.
 */
class GoogleRefundReclaimerTest {
    private val google = FakeGooglePlayClient()
    private val paymentRepository = mockk<PaymentRepository>()
    private val grantService = mockk<PurchaseGrantService>()
    private val reclaimer = GoogleRefundReclaimer(google, paymentRepository, grantService)

    /** 10개를 사서 2, 3, 5개로 나눠 환불하면 목표는 누적 환불 수량을 따라간다. 이번 환불 수량만 쓰면 덜 회수된다. */
    @Test
    fun `부분 환불의 목표는 지금까지 환불된 총 수량으로 계산한다`() {
        val granted = 3_000_000L

        assertEquals(0, GoogleRefundReclaimer.refundTarget(granted, 10, 10))
        assertEquals(600_000, GoogleRefundReclaimer.refundTarget(granted, 10, 8))
        assertEquals(1_500_000, GoogleRefundReclaimer.refundTarget(granted, 10, 5))
        assertEquals(granted, GoogleRefundReclaimer.refundTarget(granted, 10, 0))
    }

    @Test
    fun `나누어떨어지지 않으면 내림한다`() {
        assertEquals(333, GoogleRefundReclaimer.refundTarget(1_000, 3, 2))
    }

    @Test
    fun `전액 환불은 Google 을 조회하지 않고 지급량 전체를 목표로 삼는다`() {
        givenPayment(quantity = 1, grantedTokens = 300_000)
        givenApplyRefund()

        reclaimer.reclaim(TOKEN, partial = false, sourceId = SOURCE)

        verify(exactly = 1) { grantService.applyRefund(PaymentPlatform.GOOGLE, KEY, 300_000, SOURCE) }
        assertEquals(0, google.getPurchaseCalls)
    }

    @Test
    fun `부분 환불은 다시 조회한 환불 가능 수량으로 목표를 계산한다`() {
        givenPayment(quantity = 10, grantedTokens = 3_000_000)
        givenApplyRefund()
        google.stub(TOKEN, purchase(accountToken = null, quantity = 10, refundableQuantity = 8))

        reclaimer.reclaim(TOKEN, partial = true, sourceId = SOURCE)

        verify(exactly = 1) { grantService.applyRefund(PaymentPlatform.GOOGLE, KEY, 600_000, SOURCE) }
    }

    @Test
    fun `환불 가능 수량을 읽을 수 없으면 회수하지 않고 거부한다`() {
        givenPayment(quantity = 10, grantedTokens = 3_000_000)
        val unreadable =
            listOf(
                purchase(accountToken = null, quantity = 10, refundableQuantity = null),
                purchase(accountToken = null, quantity = 10, refundableQuantity = 11),
                purchase(accountToken = null, quantity = 10, refundableQuantity = -1),
                purchase(accountToken = null, quantity = 0, refundableQuantity = 0),
                lineItems(emptyList()),
                lineItems(listOf(line(10, 8), line(10, 8))),
            )

        unreadable.forEach { stubbed ->
            google.stub(TOKEN, stubbed)

            val error = assertFailsWith<BusinessException> { reclaimer.reclaim(TOKEN, partial = true, sourceId = SOURCE) }

            assertEquals(ErrorCode.PURCHASE_INVALID, error.errorCode)
        }
        verify(exactly = 0) { grantService.applyRefund(any(), any(), any(), any()) }
    }

    @Test
    fun `결제 기록이 없으면 PAYMENT_NOT_FOUND 로 끝난다`() {
        every { paymentRepository.findByPlatformAndStoreTransactionKey(PaymentPlatform.GOOGLE, KEY.value) } returns null

        val error = assertFailsWith<BusinessException> { reclaimer.reclaim(TOKEN, partial = false, sourceId = SOURCE) }

        assertEquals(ErrorCode.PAYMENT_NOT_FOUND, error.errorCode)
        verify(exactly = 0) { grantService.applyRefund(any(), any(), any(), any()) }
    }

    private fun givenPayment(
        quantity: Int,
        grantedTokens: Long,
    ) {
        every { paymentRepository.findByPlatformAndStoreTransactionKey(PaymentPlatform.GOOGLE, KEY.value) } returns
            Payment(
                subjectKey = "a".repeat(64),
                platform = PaymentPlatform.GOOGLE,
                storeTransactionKey = KEY.value,
                storePurchaseToken = TOKEN,
                storeOrderId = "GPA.0000-0000-0000-00000",
                productId = "token_300000",
                quantity = quantity,
                grantedTokens = grantedTokens,
                environment = PaymentEnvironment.PRODUCTION,
                purchasedAt = Instant.parse("2026-11-06T00:00:00Z"),
            )
    }

    private fun givenApplyRefund() {
        every { grantService.applyRefund(any(), any(), any(), any()) } answers {
            RefundResult(paymentId = 1, refundedTokens = thirdArg(), changedTokens = thirdArg())
        }
    }

    private fun lineItems(items: List<GooglePurchaseLineItem>): GooglePlayPurchase = purchase(accountToken = null).copy(lineItems = items)

    private fun line(
        quantity: Int,
        refundable: Int,
    ) = GooglePurchaseLineItem("token_300000", quantity, GoogleConsumptionState.CONSUMED, refundable)

    private companion object {
        const val TOKEN = "google-purchase-token-for-reclaimer-test"
        const val SOURCE = "voided:1"
        val KEY = StoreTransactionKey.ofGooglePurchaseToken(TOKEN)
    }
}
