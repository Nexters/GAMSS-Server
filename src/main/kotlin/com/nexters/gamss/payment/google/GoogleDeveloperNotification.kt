package com.nexters.gamss.payment.google

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * RTDN `DeveloperNotification`. Pub/Sub 메시지의 `data` 를 base64 로 풀면 나온다. 알림 하나에는 아래 알림 필드 중
 * 하나만 들어 있다. 구매 상태는 들어 있지 않다. 구매 알림과 부분 환불 알림은 Google 을 다시 조회해 판단하고, 전액
 * 환불(refundType = 1)은 본문의 purchaseToken 으로 찾은 결제를 조회 없이 전액 회수한다. 그 근거는
 * [com.nexters.gamss.payment.service.GoogleNotificationService] 에 적었다.
 *
 * 모르는 필드(구독 알림 등)는 무시한다. Google 이 알림 종류를 늘려도 읽기가 실패하지 않게 하려는 것이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleDeveloperNotification(
    val version: String? = null,
    val packageName: String? = null,
    val eventTimeMillis: String? = null,
    val oneTimeProductNotification: OneTimeProductNotification? = null,
    val voidedPurchaseNotification: VoidedPurchaseNotification? = null,
    val pendingRefundReviewNotification: PendingRefundReviewNotification? = null,
    val testNotification: Map<String, Any?>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OneTimeProductNotification(
    val notificationType: Int? = null,
    val purchaseToken: String? = null,
    val sku: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VoidedPurchaseNotification(
    val purchaseToken: String? = null,
    val orderId: String? = null,
    val productType: Int? = null,
    val refundType: Int? = null,
)

/**
 * 개발자 검토가 필요한 지불 거절(chargeback) 요청. 개발자는 24시간 안에 [pendingRefundToken] 으로 이의를 제기할 수
 * 있다. #246 은 응답하지 않으므로 [pendingRefundToken] 은 읽기만 하고 로그에도 남기지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PendingRefundReviewNotification(
    val orderId: String? = null,
    val refundReason: Int? = null,
    val pendingRefundToken: String? = null,
)
