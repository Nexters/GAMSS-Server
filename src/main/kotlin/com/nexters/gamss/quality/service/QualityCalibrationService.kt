package com.nexters.gamss.quality.service

import com.nexters.gamss.quality.domain.AwkwardType
import com.nexters.gamss.quality.domain.ResponseAiVerdict
import com.nexters.gamss.quality.domain.ResponseHumanVerdict
import com.nexters.gamss.quality.domain.ResponseVerdict
import com.nexters.gamss.quality.repository.ResponseAiVerdictRepository
import com.nexters.gamss.quality.repository.ResponseHumanVerdictRepository
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** 사람 판정이 있는 응답을 지금 채점 설정으로 채점하고, 사람 판정과 얼마나 맞는지 잰다. */
@Service
class QualityCalibrationService(
    private val qualityJudgeService: QualityJudgeService,
    private val qualityJudgeRunner: QualityJudgeRunner,
    private val humanVerdictRepository: ResponseHumanVerdictRepository,
    private val aiVerdictRepository: ResponseAiVerdictRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "quality-calibration").apply { isDaemon = true } }
    private val run = AtomicReference<CalibrationRun?>(null)

    fun start(): CalibrationRun {
        run.get()?.takeIf { it.running }?.let { return it }
        val judge = qualityJudgeService.currentJudge()
        val messageIds = humanVerdictRepository.findAll().map { it.messageId }.toSet()
        val triggers = qualityJudgeRunner.pendingTriggersOfMessages(messageIds, judge)
        val started =
            CalibrationRun(judge.view.model, judge.version, triggers.size, running = triggers.isNotEmpty(), startedAt = Instant.now())
        run.set(started)
        if (triggers.isNotEmpty()) {
            executor.submit {
                try {
                    val tally =
                        qualityJudgeRunner.run(
                            triggers,
                            judge,
                        ) { progress -> run.set(started.copy(processed = progress.processed, failed = progress.failed)) }
                    run.set(started.copy(processed = tally.processed, failed = tally.failed, running = false, aborted = tally.aborted))
                } catch (e: Exception) {
                    log.error("정답 세트 채점 실패", e)
                    run.updateAndGet { it?.copy(running = false, aborted = true) }
                }
            }
        }
        return started
    }

    fun currentRun(): CalibrationRun? = run.get()

    fun report(): CalibrationReport {
        val judge = qualityJudgeService.currentJudge()
        val humans = humanVerdictRepository.findAll()
        val ai =
            aiVerdictRepository
                .findByMessageIdInAndJudgeModelAndJudgePromptVersion(humans.map { it.messageId }.toSet(), judge.view.model, judge.version)
                .associateBy { it.messageId }
        return CalibrationMetrics.of(humans, ai, judge.view.model, judge.version)
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }
}

data class CalibrationRun(
    val judgeModel: String,
    val judgePromptVersion: Int,
    val total: Int,
    val processed: Int = 0,
    val failed: Int = 0,
    val running: Boolean,
    val aborted: Boolean = false,
    val startedAt: Instant,
)

data class CalibrationReport(
    val judgeModel: String,
    val judgePromptVersion: Int,
    val humanVerdictCount: Int,
    val comparedPairs: Int,
    val aiHumanAgreement: Double?,
    val humanHumanAgreement: Double?,
    val humanHumanPairs: Int,
    val byType: List<TypeCalibration>,
)

data class TypeCalibration(
    val type: AwkwardType,
    val humanMarked: Int,
    val missedRate: Double?,
    val aiMarked: Int,
    val overflagRate: Double?,
    val humanHumanAgreement: Double?,
)

object CalibrationMetrics {
    fun of(
        humans: List<ResponseHumanVerdict>,
        ai: Map<Long, ResponseAiVerdict>,
        judgeModel: String,
        judgePromptVersion: Int,
    ): CalibrationReport {
        val pairs = humans.mapNotNull { human -> ai[human.messageId]?.let { human to it } }
        val humanPairs =
            humans
                .groupBy { it.messageId }
                .values
                .filter { it.size >= 2 }
                .flatMap { group -> group.indices.flatMap { i -> (i + 1 until group.size).map { group[i] to group[it] } } }
        return CalibrationReport(
            judgeModel = judgeModel,
            judgePromptVersion = judgePromptVersion,
            humanVerdictCount = humans.size,
            comparedPairs = pairs.size,
            aiHumanAgreement = rate(pairs.count { (h, a) -> h.verdict == a.verdict }, pairs.size),
            humanHumanAgreement = rate(humanPairs.count { (a, b) -> a.verdict == b.verdict }, humanPairs.size),
            humanHumanPairs = humanPairs.size,
            byType =
                AwkwardType.entries.map { type ->
                    val humanMarked = pairs.filter { (h, _) -> type in h.awkwardTypes }
                    val aiMarked = pairs.filter { (_, a) -> type in a.awkwardTypes }
                    val typePairs = humanPairs.filter { (a, b) -> type in a.awkwardTypes || type in b.awkwardTypes }
                    TypeCalibration(
                        type = type,
                        humanMarked = humanMarked.size,
                        missedRate = rate(humanMarked.count { (_, a) -> a.verdict == ResponseVerdict.NATURAL }, humanMarked.size),
                        aiMarked = aiMarked.size,
                        overflagRate = rate(aiMarked.count { (h, _) -> h.verdict == ResponseVerdict.NATURAL }, aiMarked.size),
                        humanHumanAgreement =
                            rate(
                                typePairs.count { (a, b) ->
                                    type in a.awkwardTypes && type in b.awkwardTypes
                                },
                                typePairs.size,
                            ),
                    )
                },
        )
    }

    fun rate(
        count: Int,
        total: Int,
    ): Double? = if (total == 0) null else Math.round(count * 1000.0 / total) / 10.0
}
