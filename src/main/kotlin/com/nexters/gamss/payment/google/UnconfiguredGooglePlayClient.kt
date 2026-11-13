package com.nexters.gamss.payment.google

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import java.time.Instant

/**
 * 자격증명이 없는 환경(로컬, 테스트, 아직 키를 넣지 않은 배포)의 구현. 모든 호출을 503 으로 거부한다.
 *
 * 거부된 구매는 consume 되지 않아 3일 뒤 Google 이 자동 환불하므로 돈이 새지 않는다. 그래서 prod 에
 * 키를 넣는 일이 Android 결제를 여는 스위치가 된다.
 */
class UnconfiguredGooglePlayClient : GooglePlayClient {
    override val configured: Boolean = false

    override fun getPurchase(purchaseToken: String): GooglePlayPurchase = throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)

    override fun consume(
        productId: String,
        purchaseToken: String,
    ): Unit = throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)

    override fun listVoidedPurchases(
        startTime: Instant,
        endTime: Instant,
        pageToken: String?,
    ): GoogleVoidedPurchasePage = throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
}
