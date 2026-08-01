package com.nexters.gamss.conversation.service

import org.slf4j.Logger
import java.time.Instant

/**
 * [PendingCommentCleanupScheduler]·[PendingCardCleanupScheduler]가 공유하는 고아 PENDING 복구 로직
 * ([com.nexters.gamss.llm.generation.LlmRetryPolicy]와 같은 공유 방식).
 */
internal object PendingCleanupSupport {
    /** [reset]으로 threshold 이전 PENDING을 되돌리고, 한 건이라도 되돌렸으면 호출한 스케줄러의 로거로 남긴다. */
    fun resetStalePendingAndLog(
        log: Logger,
        threshold: Instant,
        label: String,
        reset: (Instant) -> Int,
    ) {
        val resetCount = reset(threshold)
        if (resetCount > 0) {
            log.warn("고아 {} PENDING {}건을 NONE으로 되돌림 (threshold={})", label, resetCount, threshold)
        }
    }
}
