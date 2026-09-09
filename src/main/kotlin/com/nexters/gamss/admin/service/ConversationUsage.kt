package com.nexters.gamss.admin.service

import com.nexters.gamss.conversation.domain.ConversationStatus
import com.nexters.gamss.notification.domain.NotificationOutcome
import java.time.Instant

/**
 * 대화방 1개의 사용량 집계 행(백오피스 대화방 사용량 페이지). 대화방 메타 + 유저/캐릭터 메시지 수 +
 * 카드 생성 여부 + 소비 토큰(총량과 캐시)을 담는다. conversation_id가 없는 과거 생성 로그는 어디에도 잡히지 않는다.
 */
data class ConversationUsage(
    val conversationId: Long,
    val memberId: Long,
    val title: String?,
    val status: ConversationStatus,
    val createdAt: Instant,
    val userMessageCount: Long,
    val characterMessageCount: Long,
    val cardCreated: Boolean,
    val totalTokens: Long,
    val cachedTokens: Long,
    /** 이 대화방에서 소비된 토큰의 예상 비용(USD). 모델별 요금표로 계산. */
    val estimatedCostUsd: Double,
    /** 04:30 미종료 리마인더 결과. 이 방이 그 알림의 대상이 아니었으면 null. */
    val reminderNotification: NotificationOutcome?,
    /** 05:00 카드 도착 알림 결과. 이 방이 그 알림의 대상이 아니었으면 null. */
    val cardNotification: NotificationOutcome?,
    /**
     * 리마인더 시각과 하루 경계 사이에 만들어져, 리마인더 대상일 수 없었던 방인지
     * ([com.nexters.gamss.card.service.AutoCardWindow.isCreatedInReminderGap]).
     *
     * 이 방은 리마인더 기록이 없으면서 상태만 종료가 되므로, 표가 "사용자가 직접 종료했다"고
     * 읽으면 틀린다. 경계 값을 화면에 복제하지 않도록 판정 결과만 내려준다.
     */
    val createdInReminderGap: Boolean,
)
