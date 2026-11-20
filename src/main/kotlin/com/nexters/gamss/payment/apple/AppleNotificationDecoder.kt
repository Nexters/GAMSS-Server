package com.nexters.gamss.payment.apple

import com.nexters.gamss.payment.domain.PaymentEnvironment
import java.time.Instant

/**
 * App Store Server Notifications V2 의 `signedPayload` 를 검증하고 푸는 경계. 라이브러리 타입은 밖으로 내보내지 않는다.
 *
 * 웹훅은 인증 없이 열려 있어 이 서명 검증이 유일한 방어선이다. 서명, 인증서 체인, bundleId·앱 ID 가 맞지 않거나
 * Production·Sandbox 가 아닌 알림이면 [InvalidAppleNotificationException] 을 던진다.
 */
interface AppleNotificationDecoder {
    fun decode(signedPayload: String): AppleNotification
}

/** 서명이 확인된 알림에서 쓰는 값만 옮긴 것. 안의 거래([signedTransactionInfo])는 따로 검증해야 한다. */
data class AppleNotification(
    val notificationUuid: String,
    /** `ONE_TIME_CHARGE`, `REFUND` 등 원문. 라이브러리가 모르는 새 종류도 그대로 받는다. */
    val type: String,
    val subtype: String?,
    val signedDate: Instant?,
    val environment: PaymentEnvironment,
    val signedTransactionInfo: String?,
)

class InvalidAppleNotificationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
