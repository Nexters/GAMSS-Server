package com.nexters.gamss.conversation.service

import org.slf4j.Logger
import java.time.Instant

/**
 * [PendingCommentCleanupScheduler]·[PendingCardCleanupScheduler]가 공유하는 고아 PENDING 복구 로직
 * ([com.nexters.gamss.llm.generation.LlmRetryPolicy]와 같은 공유 방식).
 */
internal object PendingCleanupSupport {
    /**
     * [reset]으로 threshold 이전 PENDING을 되돌리고, 한 건이라도 되돌렸으면 호출한 스케줄러의 로거로
     * 남긴다. 되돌린 건수를 반환해 호출자가 메트릭으로도 올릴 수 있게 한다 — 이 값이 0 이 아니라는 것은
     * 크래시나 배포 중단으로 생성이 끊겼다는 뜻이라, 추이로 봐야 원인 시점을 짚을 수 있다.
     */
    fun resetStalePendingAndLog(
        log: Logger,
        threshold: Instant,
        label: String,
        reset: (Instant) -> Int,
    ): Int {
        val resetCount = reset(threshold)
        if (resetCount > 0) {
            log.warn("고아 {} PENDING {}건을 NONE으로 되돌림 (threshold={})", label, resetCount, threshold)
        }
        return resetCount
    }
}
