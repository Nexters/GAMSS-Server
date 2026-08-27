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
)
