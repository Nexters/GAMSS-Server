package com.nexters.gamss.notification.service

import com.nexters.gamss.notification.domain.NotificationOutcome

/**
 * 대화방 하나가 새벽 알림 두 번에서 각각 어떻게 끝났는지.
 *
 * null 은 그 회차에 이 방이 대상이 아니었다는 뜻이다. 발송하지 않은 것과 대상이 아니었던 것은
 * 다르므로 [NotificationOutcome] 에 값을 더하지 않고 null 로 가른다.
 */
data class ConversationNotificationOutcomes(
    /** 04:30 미종료 리마인더. */
    val reminder: NotificationOutcome?,
    /** 05:00 카드 도착 알림. */
    val cardCreated: NotificationOutcome?,
)
