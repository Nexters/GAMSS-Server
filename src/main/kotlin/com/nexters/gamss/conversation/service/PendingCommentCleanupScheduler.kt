package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.config.ConversationProperties
import com.nexters.gamss.conversation.repository.MessageRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 서버 크래시·배포 중단으로 커밋까지 못 간 PENDING을 주기적으로 NONE으로 되돌려 재선점 가능하게 한다.
 * (인메모리 락과 달리 CAS는 이 흔적이 DB에 영속되므로 이런 복구가 가능하다.)
 */
@Component
class PendingCommentCleanupScheduler(
    private val messageRepository: MessageRepository,
    private val properties: ConversationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = CHECK_INTERVAL_MILLIS)
    fun resetStalePending() {
        val threshold = Instant.now().minus(properties.commentPendingTimeout)
        val resetCount = messageRepository.resetStalePending(threshold)
        if (resetCount > 0) {
            log.warn("고아 PENDING {}건을 NONE으로 되돌림 (threshold={})", resetCount, threshold)
        }
    }

    companion object {
        private const val CHECK_INTERVAL_MILLIS = 60_000L
    }
}
