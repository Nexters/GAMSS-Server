package com.nexters.gamss.conversation.service

import io.mockk.mockk
import io.mockk.verify
import org.slf4j.Logger
import java.time.Instant
import kotlin.test.Test

class PendingCleanupSupportTest {
    private val log = mockk<Logger>(relaxed = true)
    private val threshold = Instant.parse("2026-07-31T00:00:00Z")

    @Test
    fun `되돌린 건수가 0건이면 로그를 남기지 않는다`() {
        PendingCleanupSupport.resetStalePendingAndLog(log, threshold, "댓글 생성") { 0 }

        verify(exactly = 0) { log.warn(any<String>(), *anyVararg()) }
    }

    @Test
    fun `되돌린 건수가 있으면 라벨과 건수를 포함해 경고 로그를 남긴다`() {
        PendingCleanupSupport.resetStalePendingAndLog(log, threshold, "카드 생성") { 3 }

        verify(exactly = 1) { log.warn(any<String>(), "카드 생성", 3, threshold) }
    }

    @Test
    fun `reset은 주어진 threshold 그대로 호출한다`() {
        var receivedThreshold: Instant? = null

        PendingCleanupSupport.resetStalePendingAndLog(log, threshold, "댓글 생성") {
            receivedThreshold = it
            0
        }

        kotlin.test.assertEquals(threshold, receivedThreshold)
    }
}
