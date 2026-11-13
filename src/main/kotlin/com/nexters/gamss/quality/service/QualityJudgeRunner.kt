package com.nexters.gamss.quality.service

import com.nexters.gamss.conversation.service.ResponseRef
import com.nexters.gamss.conversation.service.ResponseThreadService
import com.nexters.gamss.llm.error.LlmFailureKind
import com.nexters.gamss.llm.error.llmFailureKind
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** 트리거 여러 개를 차례로 채점한다. 자동 채점 배치와 정답 세트 채점이 같이 쓴다. */
@Component
class QualityJudgeRunner(
    private val qualityJudgeService: QualityJudgeService,
    private val qualityJudgeSettingService: QualityJudgeSettingService,
    private val responseThreadService: ResponseThreadService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun pendingTriggersOf(
        responses: List<ResponseRef>,
        judge: JudgeSnapshot,
    ): List<Long> =
        qualityJudgeService.pendingTriggers(
            responses.groupBy({ it.triggerMessageId }, { it.message.id }),
            judge,
        )

    fun pendingTriggersOfMessages(
        messageIds: Collection<Long>,
        judge: JudgeSnapshot,
    ): List<Long> = pendingTriggersOf(responseThreadService.responses(messageIds).map { it.ref }, judge)

    /** 429 나 서킷 오픈이 [UPSTREAM_ABORT_STREAK]번 연달아 나면 남은 것은 두고 멈춘다. */
    fun run(
        triggerIds: List<Long>,
        judge: JudgeSnapshot,
        onProgress: (JudgeRunTally) -> Unit = {},
    ): JudgeRunTally {
        val voteCount = qualityJudgeSettingService.current().voteCount
        val tally = JudgeRunTally(total = triggerIds.size)
        var upstreamStreak = 0
        for (triggerId in triggerIds) {
            try {
                when (qualityJudgeService.judge(triggerId, judge, voteCount)) {
                    JudgeOutcome.JUDGED -> tally.judged++
                    JudgeOutcome.ALREADY_JUDGED, JudgeOutcome.SKIPPED -> tally.skipped++
                }
                upstreamStreak = 0
            } catch (e: Exception) {
                tally.failed++
                log.warn("응답 채점 실패: triggerMessageId={}", triggerId, e)
                upstreamStreak = if (e.llmFailureKind() in UPSTREAM_BLOCKED_KINDS) upstreamStreak + 1 else 0
            }
            onProgress(tally)
            if (upstreamStreak >= UPSTREAM_ABORT_STREAK) {
                tally.aborted = true
                log.warn("응답 채점 중단: 업스트림이 막혀 {}건 연달아 실패했다", upstreamStreak)
                break
            }
        }
        return tally
    }

    companion object {
        private const val UPSTREAM_ABORT_STREAK = 3
        private val UPSTREAM_BLOCKED_KINDS = setOf(LlmFailureKind.RATE_LIMITED, LlmFailureKind.CIRCUIT_OPEN)
    }
}

data class JudgeRunTally(
    val total: Int,
    var judged: Int = 0,
    var skipped: Int = 0,
    var failed: Int = 0,
    var aborted: Boolean = false,
) {
    val processed: Int get() = judged + skipped + failed
}
