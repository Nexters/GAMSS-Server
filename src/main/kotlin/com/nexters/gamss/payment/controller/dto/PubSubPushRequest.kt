package com.nexters.gamss.payment.controller.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** Pub/Sub push 본문(래핑 형식). [PubSubMessage.data] 는 base64 로 인코딩한 RTDN `DeveloperNotification` 이다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PubSubPushRequest(
    val message: PubSubMessage? = null,
    val subscription: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PubSubMessage(
    val data: String? = null,
    val messageId: String? = null,
)
