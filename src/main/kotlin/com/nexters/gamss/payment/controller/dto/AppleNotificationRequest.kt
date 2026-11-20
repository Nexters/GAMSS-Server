package com.nexters.gamss.payment.controller.dto

/** App Store Server Notifications V2 본문. */
data class AppleNotificationRequest(
    val signedPayload: String?,
)
