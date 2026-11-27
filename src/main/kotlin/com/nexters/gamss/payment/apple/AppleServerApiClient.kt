package com.nexters.gamss.payment.apple

import java.time.Duration
import java.time.Instant

/**
 * App Store Server API 경계. 지금은 알림 기록 조회(Get Notification History)만 쓴다. 라이브러리 타입은 밖으로 내보내지 않는다.
 *
 * In-App Purchase 키가 없는 환경에서는 [configured] 가 false 이고 호출할 수 없다. Apple 과 통신할 수 없으면
 * [com.nexters.gamss.global.exception.ErrorCode.PAYMENT_STORE_UNAVAILABLE] 로 던진다.
 */
interface AppleServerApiClient {
    val configured: Boolean

    /** 알림 기록을 조회할 수 있는 가장 먼 과거. Apple 은 Production 180일, Sandbox 30일까지만 허용한다. */
    val maxLookback: Duration

    /** [start] 부터 [end] 사이에 Apple 이 보낸 알림. 다음 페이지가 있으면 [AppleNotificationHistoryPage.nextPaginationToken] 이 있다. */
    fun notificationHistory(
        start: Instant,
        end: Instant,
        paginationToken: String?,
    ): AppleNotificationHistoryPage
}

data class AppleNotificationHistoryPage(
    /** 웹훅이 받는 것과 같은 `signedPayload`. */
    val signedPayloads: List<String>,
    val nextPaginationToken: String?,
)
