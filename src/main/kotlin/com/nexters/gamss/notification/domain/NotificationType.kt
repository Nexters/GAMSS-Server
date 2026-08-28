package com.nexters.gamss.notification.domain

/** 새벽 배치가 거는 알림의 종류. */
enum class NotificationType {
    /** 04:30 미종료 대화방 리마인더. 자동 종료 30분 전에 직접 마무리할 기회를 준다. */
    UNFINISHED_REMINDER,

    /** 05:00 카드 도착 알림. 배치가 방을 닫고 카드를 만든 뒤에 보낸다. */
    CARD_CREATED,
}
