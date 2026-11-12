package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.domain.PaymentEnvironment
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.domain.VerifiedPurchase
import com.nexters.gamss.payment.google.GoogleConsumptionState
import com.nexters.gamss.payment.google.GooglePlayClient
import com.nexters.gamss.payment.google.GooglePlayPurchase
import com.nexters.gamss.payment.google.GooglePurchaseState
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Google 에 구매를 직접 조회해, 지급([PurchaseGrantService])에 넘길 [VerifiedPurchase] 를 만든다.
 *
 * **Google 응답으로만 알 수 있는 것만 판정한다.** 구매 상태, 상품 ID, 수량, 테스트 구매 여부다.
 * 계정 토큰이 누구 것인지와 상품이 카탈로그에 있는지는 보지 않는다. 둘 다 지급([PaymentRecordService.grant])이
 * 이미 판정한다. 계정 토큰은 재요청인지 보기 전에 판정하고(재요청도 처음 지급과 같은 주인이어야 한다),
 * 카탈로그는 재요청인지 본 뒤에 판정한다. 여기서 카탈로그를 먼저 보면 판매를 멈춘 상품의 재요청이 실패로
 * 바뀌고, 주인이나 상품을 못 찾았을 때 지급 쪽이 남기는 수동 처리 로그도 건너뛴다.
 * 요청 회원을 받지 않으므로 RTDN 웹훅(#246)도 이 검증기를 그대로 쓴다.
 */
@Component
class GooglePurchaseVerifier(
    private val client: GooglePlayClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * [expectedProductId] 는 앱이 보낸 상품 ID 다. 웹훅처럼 기대값이 없으면 null 로 부른다.
     *
     * PURCHASED 가 아니면 지급하지 않는다. PENDING 은 대기로 돌려주고, CANCELLED 와 모르는 상태는
     * 거부한다. PENDING 이 결제를 마치면 앱이 다시 보내거나 RTDN(#246)이 가져온다.
     */
    fun verify(
        purchaseToken: String,
        expectedProductId: String?,
    ): GooglePurchaseVerification {
        val purchase = client.getPurchase(purchaseToken)
        val key = StoreTransactionKey.ofGooglePurchaseToken(purchaseToken)
        return when (purchase.purchaseState) {
            GooglePurchaseState.PURCHASED -> {
                verified(purchaseToken, key, purchase, expectedProductId)
            }

            GooglePurchaseState.PENDING -> {
                GooglePurchaseVerification.Pending(productIdOf(purchase, key, expectedProductId))
            }

            GooglePurchaseState.CANCELLED -> {
                throw BusinessException(ErrorCode.PURCHASE_CANCELLED)
            }

            GooglePurchaseState.UNSPECIFIED -> {
                log.warn("Google 구매 상태를 알 수 없어 지급하지 않았습니다: key={}, storeOrderId={}", key, purchase.orderId)
                throw BusinessException(ErrorCode.PURCHASE_INVALID)
            }
        }
    }

    private fun verified(
        purchaseToken: String,
        key: StoreTransactionKey,
        purchase: GooglePlayPurchase,
        expectedProductId: String?,
    ): GooglePurchaseVerification.Verified {
        val productId = productIdOf(purchase, key, expectedProductId)
        val item = purchase.lineItems.single()
        val quantity = quantityOf(item.quantity, key, purchase, productId)
        val purchasedAt =
            purchase.purchaseCompletionTime ?: run {
                log.error("PURCHASED 인데 구매 완료 시각이 없습니다: key={}, storeOrderId={}", key, purchase.orderId)
                throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
            }
        return GooglePurchaseVerification.Verified(
            purchase =
                VerifiedPurchase(
                    platform = PaymentPlatform.GOOGLE,
                    storeTransactionKey = key,
                    accountToken = purchase.obfuscatedExternalAccountId,
                    productId = productId,
                    quantity = quantity,
                    environment = if (purchase.testPurchase) PaymentEnvironment.SANDBOX else PaymentEnvironment.PRODUCTION,
                    purchasedAt = purchasedAt,
                    storeOrderId = purchase.orderId,
                    storePurchaseToken = purchaseToken,
                ),
            consumed = item.consumptionState == GoogleConsumptionState.CONSUMED,
        )
    }

    /**
     * 일회성 상품 구매에는 상품이 하나만 들어 있다. 0개나 여러 개면 우리가 아는 구매 형태가 아니다.
     * 돈은 나갔을 수 있으나 무엇을 지급할지 정할 수 없으므로 거부하고, 수동 처리 대상으로 ERROR 를 남긴다.
     * 앱이 다른 상품 ID 를 보낸 것과는 원인이 달라 에러 코드도 나눈다.
     */
    private fun productIdOf(
        purchase: GooglePlayPurchase,
        key: StoreTransactionKey,
        expectedProductId: String?,
    ): String {
        val productId = purchase.lineItems.singleOrNull()?.productId
        if (productId.isNullOrBlank()) {
            log.error(
                "Google 구매의 상품 구성을 알 수 없어 지급하지 않았습니다. 수동 처리가 필요합니다: key={}, storeOrderId={}, lineItems={}",
                key,
                purchase.orderId,
                purchase.lineItems.size,
            )
            throw BusinessException(ErrorCode.PURCHASE_INVALID)
        }
        if (expectedProductId != null && productId != expectedProductId) {
            log.warn(
                "Google 구매의 상품이 요청과 달라 지급하지 않았습니다: key={}, storeOrderId={}, productId={}, expectedProductId={}",
                key,
                purchase.orderId,
                productId,
                expectedProductId,
            )
            throw BusinessException(ErrorCode.PURCHASE_PRODUCT_MISMATCH)
        }
        return productId
    }

    /**
     * 다중 수량은 Play Console 에서 꺼 두었으므로 1 이 아닌 구매가 오면 설정이 바뀐 것이다. 서버와 앱이
     * 수량을 처리하는지 확인하기 전에는 지급하지 않는다(#244). 돈이 나간 구매라 수동 처리 대상으로
     * ERROR 를 남긴다.
     *
     * 값이 없으면 1 로 본다. v1 은 '없으면 1'이라고 문서화돼 있고 v2 는 아무 말이 없다. 없는 값을
     * 거부하면 모든 Android 구매가 막히고 3일 뒤 자동 환불된다.
     *
     * 다중 수량을 켤 때는 부분 환불 회수(RTDN refundType 2, 대사 항목의 voidedQuantity, refundableQuantity 로 계산하는
     * 목표)도 함께 확인한다. 지금은 수량 1 만 지급하므로 그 경로는 테스트로만 돈다([GoogleRefundReclaimer]).
     */
    private fun quantityOf(
        quantity: Int?,
        key: StoreTransactionKey,
        purchase: GooglePlayPurchase,
        productId: String,
    ): Int {
        if (quantity == null) {
            log.warn("Google 구매 응답에 수량이 없어 1 로 처리합니다: key={}, storeOrderId={}", key, purchase.orderId)
            return 1
        }
        if (quantity != 1) {
            log.error(
                "여러 개를 산 Google 구매라 지급하지 않았습니다. 수동 처리가 필요합니다: " +
                    "key={}, storeOrderId={}, productId={}, quantity={}, accountTokenPresent={}",
                key,
                purchase.orderId,
                productId,
                quantity,
                purchase.obfuscatedExternalAccountId != null,
            )
            throw BusinessException(ErrorCode.PURCHASE_QUANTITY_UNSUPPORTED)
        }
        return quantity
    }
}

sealed interface GooglePurchaseVerification {
    /** 지급할 수 있는 구매. [consumed] 면 이미 consume 된 것이라 consume 을 다시 부르지 않는다. */
    class Verified(
        val purchase: VerifiedPurchase,
        val consumed: Boolean,
    ) : GooglePurchaseVerification

    /** 결제가 아직 끝나지 않은 구매. 지급하지 않는다. */
    class Pending(
        val productId: String,
    ) : GooglePurchaseVerification
}
