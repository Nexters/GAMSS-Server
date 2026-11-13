package com.nexters.gamss.quality.service

import com.nexters.gamss.conversation.service.ResponseThreadService
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.ZonedDateTime

/** 매일 06:00 에 최근 응답을 채점한다. 05:00 자동 카드 배치와 같은 쿼터를 나눠 쓰므로 시간을 뗐다. */
@Component
class QualityJudgeScheduler(
    private val qualityJudgeSettingService: QualityJudgeSettingService,
    private val qualityJudgeService: QualityJudgeService,
    private val qualityJudgeRunner: QualityJudgeRunner,
    private val responseThreadService: ResponseThreadService,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val batchTimer = Timer.builder("gamss.quality.judge.batch").description("응답 채점 배치 1회 소요 시간").register(meterRegistry)

    @Scheduled(cron = CRON, zone = ZONE_ID)
    fun judgeRecentResponses() {
        if (!qualityJudgeSettingService.current().autoEnabled) {
            return
        }
        runFor(ZonedDateTime.now(ZONE))
    }

    fun runFor(now: ZonedDateTime): JudgeRunTally? {
        val sample = Timer.start(meterRegistry)
        try {
            val to = dayBoundary(now)
            val from = to.minusDays(LOOKBACK_DAYS)
            val judge = qualityJudgeService.currentJudge()
            val responses = responseThreadService.responsesCreatedBetween(from.toInstant(), to.toInstant())
            val triggers = qualityJudgeRunner.pendingTriggersOf(responses, judge).take(MAX_TRIGGERS_PER_RUN)
            if (triggers.isEmpty()) {
                log.info("응답 채점 배치: 대상 없음 ({}~{})", from, to)
                return null
            }
            val tally = qualityJudgeRunner.run(triggers, judge)
            meterRegistry.counter("gamss.quality.judge.outcome", "outcome", "JUDGED").increment(tally.judged.toDouble())
            meterRegistry.counter("gamss.quality.judge.outcome", "outcome", "FAILED").increment(tally.failed.toDouble())
            log.info(
                "응답 채점 배치 {}: 대상={}, 채점={}, 건너뜀={}, 실패={}",
                if (tally.aborted) "중단" else "완료",
                tally.total,
                tally.judged,
                tally.skipped,
                tally.failed,
            )
            return tally
        } finally {
            sample.stop(batchTimer)
        }
    }

    private fun dayBoundary(now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(DAY_BOUNDARY_HOUR, 0).atZone(ZONE)
        return if (now < today) today.minusDays(1) else today
    }

    companion object {
        const val ZONE_ID = "Asia/Seoul"
        private val ZONE: ZoneId = ZoneId.of(ZONE_ID)
        private const val CRON = "0 0 6 * * *"
        private const val DAY_BOUNDARY_HOUR = 5

        // 하루를 놓쳐도 다음 실행이 이어서 보도록 며칠을 함께 본다. 이미 채점한 응답은 건너뛴다.
        private const val LOOKBACK_DAYS = 3L
        private const val MAX_TRIGGERS_PER_RUN = 500
    }
}
