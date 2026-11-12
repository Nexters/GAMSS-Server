package com.nexters.gamss.payment.domain

/** RTDN `DeveloperNotification` 에 실린 알림 종류. 처리 기록(google_notification.notification_kind)에 남긴다. */
enum class GoogleNotificationKind {
    ONE_TIME_PRODUCT,
    VOIDED_PURCHASE,

    /** 개발자 검토가 필요한 지불 거절 요청. 응답하지 않고 기록만 한다. */
    PENDING_REFUND_REVIEW,
    TEST,
    OTHER,
}
