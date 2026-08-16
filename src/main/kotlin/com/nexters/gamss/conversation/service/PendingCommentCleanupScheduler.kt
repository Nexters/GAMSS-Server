package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.config.ConversationProperties
import com.nexters.gamss.conversation.repository.MessageRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
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
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 되돌린 고아 PENDING 건수. 평소 0 이다가 튀면 그 시각에 앱이 끊겼다는 뜻이라 배포·크래시의 흔적이 된다.
    private val staleResetCounter =
        Counter
            .builder("gamss.pending.stale.reset")
            .description("타임아웃으로 NONE 으로 되돌린 고아 PENDING 건수")
            .tag("kind", "comment")
            .register(meterRegistry)

    @Scheduled(fixedDelay = CHECK_INTERVAL_MILLIS)
    fun resetStalePending() {
        val threshold = Instant.now().minus(properties.pendingGenerationTimeout)
        val resetCount =
            PendingCleanupSupport.resetStalePendingAndLog(log, threshold, "댓글 생성") {
                messageRepository.resetStalePending(it)
            }
        staleResetCounter.increment(resetCount.toDouble())
    }

    companion object {
        private const val CHECK_INTERVAL_MILLIS = 60_000L
    }
}
