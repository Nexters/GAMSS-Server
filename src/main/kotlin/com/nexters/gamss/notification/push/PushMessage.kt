package com.nexters.gamss.notification.push

/**
 * 기기에 띄울 알림 한 건의 내용.
 *
 * 누구에게 보낼지는 여기 없다 — 같은 문구를 여러 기기로 보내는 것이 기본이라 대상은
 * [PushSender.send] 의 인자로 따로 받는다.
 */
data class PushMessage(
    val title: String,
    val body: String,
)
