package com.nexters.gamss.support

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.google.GoogleConsumptionState
import com.nexters.gamss.payment.google.GooglePlayClient
import com.nexters.gamss.payment.google.GooglePlayPurchase
import com.nexters.gamss.payment.google.GooglePurchaseLineItem
import com.nexters.gamss.payment.google.GooglePurchaseState
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant

/**
 * 통합 테스트에서 실제 Google Play 호출([com.nexters.gamss.payment.google.AndroidPublisherGooglePlayClient])
 * 대신 쓰는 가짜 구현. 토큰마다 응답을 심어 두고, consume 호출과 실패를 기록·조작한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class FakeGooglePlayClientConfig {
    @Bean
    @Primary
    fun fakeGooglePlayClient(): FakeGooglePlayClient = FakeGooglePlayClient()
}

class FakeGooglePlayClient : GooglePlayClient {
    /** false 면 자격증명이 없는 환경처럼 보인다. */
    override var configured: Boolean = true

    private val purchases = mutableMapOf<String, GooglePlayPurchase>()

    /** consume 을 부른 (productId, purchaseToken). */
    val consumeCalls = mutableListOf<Pair<String, String>>()

    /** consume 을 부를 때 트랜잭션이 열려 있었는지. */
    val consumeInTransaction = mutableListOf<Boolean>()

    /** consume 이 던질 예외. [BusinessException] 이면 Google 오류, 그 밖이면 예상하지 못한 오류다. */
    var consumeFailure: RuntimeException? = null

    /** 구매 조회 직후에 실행한다. 조회와 기록 사이에 다른 요청이 끼어드는 상황을 만들 때 쓴다. */
    var afterGetPurchase: (() -> Unit)? = null

    /** 구매 조회가 Google 장애로 실패하게 한다. */
    var getPurchaseUnavailable = false

    var getPurchaseCalls = 0

    fun stub(
        purchaseToken: String,
        purchase: GooglePlayPurchase,
    ) {
        purchases[purchaseToken] = purchase
    }

    fun reset() {
        purchases.clear()
        consumeCalls.clear()
        consumeInTransaction.clear()
        configured = true
        consumeFailure = null
        afterGetPurchase = null
        getPurchaseUnavailable = false
        getPurchaseCalls = 0
    }

    override fun getPurchase(purchaseToken: String): GooglePlayPurchase {
        getPurchaseCalls++
        if (getPurchaseUnavailable) {
            throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
        }
        val purchase = purchases[purchaseToken] ?: throw BusinessException(ErrorCode.PURCHASE_INVALID)
        afterGetPurchase?.invoke()
        return purchase
    }

    override fun consume(
        productId: String,
        purchaseToken: String,
    ) {
        consumeInTransaction += TransactionSynchronizationManager.isActualTransactionActive()
        consumeCalls += productId to purchaseToken
        consumeFailure?.let { throw it }
        purchases[purchaseToken]?.let { purchase ->
            purchases[purchaseToken] =
                purchase.copy(lineItems = purchase.lineItems.map { it.copy(consumptionState = GoogleConsumptionState.CONSUMED) })
        }
    }

    companion object {
        fun purchase(
            accountToken: String?,
            productId: String = "token_300000",
            state: GooglePurchaseState = GooglePurchaseState.PURCHASED,
            quantity: Int? = 1,
            consumption: GoogleConsumptionState = GoogleConsumptionState.YET_TO_BE_CONSUMED,
            testPurchase: Boolean = false,
            completedAt: Instant? = Instant.parse("2026-11-06T00:00:00Z"),
            orderId: String? = "GPA.0000-0000-0000-00000",
        ) = GooglePlayPurchase(
            purchaseState = state,
            orderId = orderId,
            obfuscatedExternalAccountId = accountToken,
            purchaseCompletionTime = if (state == GooglePurchaseState.PENDING) null else completedAt,
            testPurchase = testPurchase,
            lineItems = listOf(GooglePurchaseLineItem(productId, quantity, consumption)),
        )
    }
}
