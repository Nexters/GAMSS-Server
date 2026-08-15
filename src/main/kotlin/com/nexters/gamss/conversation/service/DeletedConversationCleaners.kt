package com.nexters.gamss.conversation.service

import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 등록된 [DeletedConversationCleaner] 전체를 감싸는 일급 컬렉션
 * ([com.nexters.gamss.member.service.WithdrawnMemberCleaners]와 같은 이유로 둔다).
 *
 * 정리 대상이 늘어도 구현체만 추가하면 되고 [ConversationService]의 생성자·메서드는 그대로다.
 */
@Component
class DeletedConversationCleaners(
    private val cleaners: List<DeletedConversationCleaner>,
) {
    fun cleanAll(
        conversationIds: Collection<Long>,
        deletedAt: Instant,
    ) {
        if (conversationIds.isEmpty()) {
            return
        }
        cleaners.forEach { it.clean(conversationIds, deletedAt) }
    }
}
