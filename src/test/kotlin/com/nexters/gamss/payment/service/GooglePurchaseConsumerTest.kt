package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.google.GoogleConsumptionState
import com.nexters.gamss.payment.google.GooglePlayClient
import com.nexters.gamss.payment.repository.PaymentRepository
import com.nexters.gamss.support.FakeGooglePlayClient
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.QueryTimeoutException
import org.springframework.transaction.support.TransactionSynchronizationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GooglePurchaseConsumerTest {
    private val client = mockk<GooglePlayClient>()
    private val paymentRepository = mockk<PaymentRepository>()
    private val meterRegistry = SimpleMeterRegistry()
    private val consumer = GooglePurchaseConsumer(client, paymentRepository, meterRegistry)

    @Test
    fun `consume 이 끝나면 기록을 남기고 CONSUMED 를 돌려준다`() {
        justRun { client.consume(PRODUCT_ID, TOKEN) }
        every { paymentRepository.markConsumed(PAYMENT_ID, any()) } returns 1

        assertEquals(GoogleConsumeOutcome.CONSUMED, consume(alreadyConsumed = false))

        verify(exactly = 1) { paymentRepository.markConsumed(PAYMENT_ID, any()) }
        assertEquals(0.0, failureCount())
    }

    @Test
    fun `이미 consume 된 구매는 Google 을 부르지 않고 기록만 남긴다`() {
        every { paymentRepository.markConsumed(PAYMENT_ID, any()) } returns 1

        assertEquals(GoogleConsumeOutcome.CONSUMED, consume(alreadyConsumed = true))

        verify(exactly = 0) { client.consume(any(), any()) }
    }

    @Test
    fun `Google 과 통신할 수 없으면 실패를 세고 STORE_UNAVAILABLE 을 돌려준다`() {
        every { client.consume(PRODUCT_ID, TOKEN) } throws BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)

        assertEquals(GoogleConsumeOutcome.STORE_UNAVAILABLE, consume(alreadyConsumed = false))

        verify(exactly = 0) { paymentRepository.markConsumed(any(), any()) }
        assertEquals(1.0, failureCount())
    }

    @Test
    fun `Google 이 consume 을 거부하면 실패를 세고 FAILED 를 돌려준다`() {
        every { client.consume(PRODUCT_ID, TOKEN) } throws BusinessException(ErrorCode.PURCHASE_INVALID)
        every { client.getPurchase(TOKEN) } returns purchase(GoogleConsumptionState.YET_TO_BE_CONSUMED)

        assertEquals(GoogleConsumeOutcome.FAILED, consume(alreadyConsumed = false))

        verify(exactly = 0) { paymentRepository.markConsumed(any(), any()) }
        assertEquals(1.0, failureCount())
    }

    /** 앱 요청과 RTDN 이 거의 동시에 같은 구매를 consume 하면, 늦은 쪽의 consume 은 Google 이 거부한다. */
    @Test
    fun `다른 경로가 먼저 consume 했으면 실패로 세지 않고 기록만 채운다`() {
        every { client.consume(PRODUCT_ID, TOKEN) } throws BusinessException(ErrorCode.PURCHASE_INVALID)
        every { client.getPurchase(TOKEN) } returns purchase(GoogleConsumptionState.CONSUMED)
        every { paymentRepository.markConsumed(PAYMENT_ID, any()) } returns 1

        assertEquals(GoogleConsumeOutcome.CONSUMED, consume(alreadyConsumed = false))

        verify(exactly = 1) { paymentRepository.markConsumed(PAYMENT_ID, any()) }
        assertEquals(0.0, failureCount())
    }

    /** 다시 조회할 때 Google 과 통신할 수 없으면 그것도 장애라, 스케줄러가 회차를 멈추도록 STORE_UNAVAILABLE 을 돌려준다. */
    @Test
    fun `거부 뒤 다시 조회할 때 Google 과 통신할 수 없으면 실패를 세고 STORE_UNAVAILABLE 을 돌려준다`() {
        every { client.consume(PRODUCT_ID, TOKEN) } throws BusinessException(ErrorCode.PURCHASE_INVALID)
        every { client.getPurchase(TOKEN) } throws BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)

        assertEquals(GoogleConsumeOutcome.STORE_UNAVAILABLE, consume(alreadyConsumed = false))

        verify(exactly = 0) { paymentRepository.markConsumed(any(), any()) }
        assertEquals(1.0, failureCount())
    }

    @Test
    fun `거부 뒤 다시 조회해도 확인할 수 없으면 원래대로 실패로 센다`() {
        listOf<RuntimeException>(BusinessException(ErrorCode.PURCHASE_INVALID), IllegalArgumentException("응답 매핑 오류")).forEachIndexed {
            i,
            error,
            ->
            every { client.consume(PRODUCT_ID, TOKEN) } throws BusinessException(ErrorCode.PURCHASE_INVALID)
            every { client.getPurchase(TOKEN) } throws error

            assertEquals(GoogleConsumeOutcome.FAILED, consume(alreadyConsumed = false), error.javaClass.simpleName)

            assertEquals(i + 1.0, failureCount())
        }
        verify(exactly = 0) { paymentRepository.markConsumed(any(), any()) }
    }

    @Test
    fun `Google 과 통신할 수 없을 때는 다시 조회하지 않는다`() {
        every { client.consume(PRODUCT_ID, TOKEN) } throws BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)

        consume(alreadyConsumed = false)

        verify(exactly = 0) { client.getPurchase(any()) }
    }

    /** Google 쪽은 consume 됐으므로 consume 실패로 세지 않는다. 다음 회차가 상태를 보고 기록을 채운다. */
    @Test
    fun `consume 은 끝났는데 기록하지 못하면 FAILED 를 돌려준다`() {
        justRun { client.consume(PRODUCT_ID, TOKEN) }
        every { paymentRepository.markConsumed(PAYMENT_ID, any()) } throws QueryTimeoutException("timeout")

        assertEquals(GoogleConsumeOutcome.FAILED, consume(alreadyConsumed = false))

        assertEquals(0.0, failureCount())
    }

    /** 트랜잭션 안에서 부르면 Google 이 느릴 때 커넥션을 붙잡고, 지급이 롤백돼도 consume 은 되돌릴 수 없다. */
    @Test
    fun `트랜잭션 안에서 부르면 거부한다`() {
        TransactionSynchronizationManager.setActualTransactionActive(true)
        try {
            assertFailsWith<IllegalStateException> { consume(alreadyConsumed = false) }
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false)
        }

        verify(exactly = 0) { client.consume(any(), any()) }
    }

    private fun consume(alreadyConsumed: Boolean) = consumer.consume(PAYMENT_ID, PRODUCT_ID, TOKEN, alreadyConsumed)

    private fun purchase(consumption: GoogleConsumptionState) =
        FakeGooglePlayClient.purchase(accountToken = null, consumption = consumption)

    private fun failureCount(): Double = meterRegistry.counter(GooglePurchaseConsumer.FAILURE_METRIC).count()

    private companion object {
        const val PAYMENT_ID = 7L
        const val PRODUCT_ID = "token_300000"
        const val TOKEN = "purchase-token"
    }
}
