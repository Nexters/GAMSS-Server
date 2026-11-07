package com.nexters.gamss.payment.domain

import java.time.Instant

/**
 * 스토어 검증(#243, #244)을 통과한 구매. 지급([com.nexters.gamss.payment.service.PurchaseGrantService])의 입력이다.
 *
 * 모든 값은 클라이언트가 아니라 스토어 응답에서 꺼낸다. [accountToken] 은 구매에 실린 계정 토큰
 * 원문이라 없거나 형식이 틀릴 수 있고, 그런 구매는 지급하지 않는다.
 */
class VerifiedPurchase(
    val platform: PaymentPlatform,
    val storeTransactionKey: StoreTransactionKey,
    val accountToken: String?,
    val productId: String,
    val quantity: Int,
    val environment: PaymentEnvironment,
    val purchasedAt: Instant,
    /** Google `orderId`. 고객 문의 대응용이다. */
    val storeOrderId: String? = null,
    /** Google `purchaseToken` 원문. consume 과 재조회에 쓴다. */
    val storePurchaseToken: String? = null,
) {
    init {
        require(productId.isNotBlank()) { "상품 ID 는 비어 있을 수 없습니다." }
        require(quantity > 0) { "구매 수량은 양수여야 합니다: $quantity" }
        // Google 거래 키는 해시라 원문 없이는 consume 재시도도 재조회도 할 수 없다. 기본값이 null 이라
        // 빠뜨려도 컴파일되므로 여기서 막는다.
        require(platform != PaymentPlatform.GOOGLE || !storePurchaseToken.isNullOrBlank()) {
            "Google 구매는 purchaseToken 원문이 있어야 합니다."
        }
    }
}
