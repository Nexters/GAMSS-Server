package com.nexters.gamss.notification.service

import com.nexters.gamss.notification.domain.NotificationType
import com.nexters.gamss.notification.repository.NotificationLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 쌓인 알림 기록을 **집계해서 돌려준다.** 백오피스 대화방별 사용량이 쓴다.
 *
 * 남기는 쪽은 [NotificationLogRecorder] 다. 이 모듈에는 컨트롤러가 없어서 "남긴다"와 "집계한다"
 * 둘로 갈리고, 그 둘은 바뀌는 이유가 다르다.
 *
 * 다른 모듈이 [NotificationLogRepository] 를 직접 잡지 않게 하는 역할도 겸한다. 엔티티도 밖으로
 * 내보내지 않는다. 밖에서 필요한 것은 결과값뿐이라, 엔티티를 넘기면 저장 구조를 바꿀 때 쓰지도
 * 않는 필드 때문에 다른 모듈이 깨진다.
 */
@Service
@Transactional(readOnly = true)
class NotificationLogStatsService(
    private val notificationLogRepository: NotificationLogRepository,
) {
    /**
     * [conversationIds] 각각의 알림 결과. 기록이 하나도 없는 방은 맵에 없다.
     *
     * 재발송이 생기면 같은 (방, 종류)에 줄이 여러 개 쌓이는데, 보여줄 것은 마지막 결과다.
     */
    fun findOutcomesByConversation(conversationIds: Collection<Long>): Map<Long, ConversationNotificationOutcomes> {
        if (conversationIds.isEmpty()) {
            return emptyMap()
        }
        return notificationLogRepository
            .findLatestByConversationIdIn(conversationIds)
            .groupBy { it.conversationId }
            .mapValues { (_, logs) ->
                ConversationNotificationOutcomes(
                    reminder = logs.firstOrNull { it.type == NotificationType.UNFINISHED_REMINDER }?.outcome,
                    cardCreated = logs.firstOrNull { it.type == NotificationType.CARD_CREATED }?.outcome,
                )
            }
    }
}
