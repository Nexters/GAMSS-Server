package com.nexters.gamss.payment.service

import com.nexters.gamss.payment.apple.AppleNotification
import org.springframework.stereotype.Component

/**
 * App Store 알림 종류 하나 이상을 맡는 처리기. 새 종류는 구현을 추가하면 되고 [AppleNotificationService] 는 그대로다.
 *
 * 다시 받아야 하는 일시적인 실패는 `PAYMENT_STORE_UNAVAILABLE` 로, 다시 받아도 같은 실패는 다른
 * [com.nexters.gamss.global.exception.BusinessException] 으로 던진다.
 */
interface AppleNotificationHandler {
    val types: Set<String>

    fun handle(notification: AppleNotification)
}

/** 등록된 [AppleNotificationHandler] 를 알림 종류로 찾는다. 한 종류를 두 처리기가 맡으면 기동이 실패한다. */
@Component
class AppleNotificationHandlers(
    handlers: List<AppleNotificationHandler>,
) {
    private val byType: Map<String, AppleNotificationHandler> =
        handlers
            .flatMap { handler -> handler.types.map { it to handler } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (type, owners) ->
                check(owners.size == 1) { "App Store 알림 $type 을 여러 처리기가 맡습니다: ${owners.map { it.javaClass.simpleName }}" }
                owners.single()
            }

    /** 맡은 처리기가 있으면 처리하고 true, 없으면 false 다. */
    fun handle(notification: AppleNotification): Boolean {
        val handler = byType[notification.type] ?: return false
        handler.handle(notification)
        return true
    }
}
