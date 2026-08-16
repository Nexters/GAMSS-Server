package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.config.ConversationProperties
import com.nexters.gamss.conversation.repository.ConversationRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
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
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 댓글 쪽([PendingCommentCleanupScheduler])과 같은 지표를 kind 라벨로만 구분해 올린다.
    private val staleResetCounter =
        Counter
            .builder("gamss.pending.stale.reset")
            .description("타임아웃으로 NONE 으로 되돌린 고아 PENDING 건수")
            .tag("kind", "card")
            .register(meterRegistry)

    @Scheduled(fixedDelay = CHECK_INTERVAL_MILLIS)
    fun resetStalePending() {
        val threshold = Instant.now().minus(properties.pendingGenerationTimeout)
        val resetCount =
            PendingCleanupSupport.resetStalePendingAndLog(
                log,
                threshold,
                "카드 생성",
            ) { conversationRepository.resetStaleCardGenerationPending(it) }
        staleResetCounter.increment(resetCount.toDouble())
    }

    companion object {
        private const val CHECK_INTERVAL_MILLIS = 60_000L
    }
}
