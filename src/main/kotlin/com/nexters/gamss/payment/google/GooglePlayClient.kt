package com.nexters.gamss.payment.google

import java.time.Instant

/**
 * Google Play Developer API 경계. 구매 조회는 v2(`purchases.productsv2.getproductpurchasev2`),
 * consume 은 v1(`purchases.products.consume`)이다. acknowledge 와 consume 은 v1 리소스에만 있다.
 *
 * Google 오류는 이 경계에서 우리 에러 코드로 바꿔 던진다. 잘못된 토큰(400·404·410)은
 * [com.nexters.gamss.global.exception.ErrorCode.PURCHASE_INVALID], 나머지(인증 실패·429·5xx·네트워크·
 * 타임아웃)는 [com.nexters.gamss.global.exception.ErrorCode.PAYMENT_STORE_UNAVAILABLE] 이다.
 */
interface GooglePlayClient {
    /** 자격증명이 있어 실제로 Google 을 부를 수 있는가. 아니면 스케줄러는 아무것도 하지 않는다. */
    val configured: Boolean

    fun getPurchase(purchaseToken: String): GooglePlayPurchase

    /** 이미 consume 된 구매에 다시 불렀을 때의 응답은 문서화돼 있지 않다. 그래서 부르기 전에 상태를 본다. */
    fun consume(
        productId: String,
        purchaseToken: String,
    )
}

/** `ProductPurchaseV2` 에서 쓰는 값만 옮긴 것. 중첩 객체가 비어 있으면 UNSPECIFIED·null 로 채운다. */
data class GooglePlayPurchase(
    val purchaseState: GooglePurchaseState,
    val orderId: String?,
    val obfuscatedExternalAccountId: String?,
    /** PURCHASED 가 된 시각. PENDING 동안에는 없다. */
    val purchaseCompletionTime: Instant?,
    /** `testPurchaseContext` 가 있으면 라이선스 테스터의 테스트 구매다. */
    val testPurchase: Boolean,
    val lineItems: List<GooglePurchaseLineItem>,
)

data class GooglePurchaseLineItem(
    val productId: String?,
    /** v1 은 '없으면 1'이라고 문서화돼 있지만 v2 는 그런 말이 없다. 없으면 null 로 두고 해석은 검증기가 한다. */
    val quantity: Int?,
    val consumptionState: GoogleConsumptionState,
)

enum class GooglePurchaseState {
    PURCHASED,
    PENDING,
    CANCELLED,
    UNSPECIFIED,
    ;

    companion object {
        /** 모르는 값(Google 이 상태를 새로 추가한 경우 등)은 [UNSPECIFIED] 로 보고 지급하지 않는다. */
        fun of(value: String?): GooglePurchaseState = entries.firstOrNull { it.name == value } ?: UNSPECIFIED
    }
}

enum class GoogleConsumptionState(
    private val apiValue: String,
) {
    YET_TO_BE_CONSUMED("CONSUMPTION_STATE_YET_TO_BE_CONSUMED"),
    CONSUMED("CONSUMPTION_STATE_CONSUMED"),
    UNSPECIFIED("CONSUMPTION_STATE_UNSPECIFIED"),
    ;

    companion object {
        fun of(value: String?): GoogleConsumptionState = entries.firstOrNull { it.apiValue == value } ?: UNSPECIFIED
    }
}
