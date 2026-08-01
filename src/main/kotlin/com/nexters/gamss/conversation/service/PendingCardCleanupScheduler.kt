package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.config.ConversationProperties
import com.nexters.gamss.conversation.repository.ConversationRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * [PendingCommentCleanupScheduler]와 같은 목적 — 서버 크래시·배포 중단으로 커밋까지 못 간 카드
 * 생성 PENDING을 주기적으로 NONE으로 되돌려 재선점 가능하게 한다.
 */
@Component
class PendingCardCleanupScheduler(
    private val conversationRepository: ConversationRepository,
    private val properties: ConversationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = CHECK_INTERVAL_MILLIS)
    fun resetStalePending() {
        val threshold = Instant.now().minus(properties.pendingGenerationTimeout)
        PendingCleanupSupport.resetStalePendingAndLog(
            log,
            threshold,
            "카드 생성",
        ) { conversationRepository.resetStaleCardGenerationPending(it) }
    }

    companion object {
        private const val CHECK_INTERVAL_MILLIS = 60_000L
    }
}
