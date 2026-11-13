package com.nexters.gamss.admin.service

import com.nexters.gamss.conversation.service.ResponseThreadService
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.config.GeminiPricing
import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.llm.settings.PromptRevisionService
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.service.GenerationLogStatsService
import com.nexters.gamss.quality.domain.AwkwardType
import com.nexters.gamss.quality.domain.ResponseAiVerdict
import com.nexters.gamss.quality.domain.ResponseVerdict
import com.nexters.gamss.quality.service.CalibrationMetrics
import com.nexters.gamss.quality.service.QualityCalibrationService
import com.nexters.gamss.quality.service.QualityReviewService
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToLong

/** 대화 품질 탭 상단 지표. 응답마다 가장 최근 AI 판정을 쓴다. */
@Service
class ResponseQualityStatsService(
    private val qualityReviewService: QualityReviewService,
    private val qualityCalibrationService: QualityCalibrationService,
    private val promptRevisionService: PromptRevisionService,
    private val responseThreadService: ResponseThreadService,
    private val generationLogStatsService: GenerationLogStatsService,
    private val geminiPricing: GeminiPricing,
) {
    fun stats(days: Int): ResponseQualityStats {
        val today = KstDashboardDates.today()
        val from = KstDashboardDates.daysAgoStart(today, days)
        val previousFrom = KstDashboardDates.daysAgoStart(today, days * 2)
        val now = Instant.now()
        val latest =
            qualityReviewService
                .aiVerdictsCreatedBetween(previousFrom, now)
                .groupBy { it.messageId }
                .values
                .map { verdicts -> verdicts.maxBy { it.id } }
        val (current, previous) = latest.partition { it.messageCreatedAt >= from }
        val awkward = current.filter { it.verdict == ResponseVerdict.AWKWARD }
        val calibration = qualityCalibrationService.report()
        val judgeCost =
            generationLogStatsService.findByTypeSince(GenerationType.QUALITY_JUDGE, from).sumOf {
                geminiPricing.costUsd(it.model, it.inputTokens ?: 0, it.cachedTokens ?: 0, it.outputTokens ?: 0)
            }
        return ResponseQualityStats(
            days = days,
            judgedCount = current.size,
            awkwardCount = awkward.size,
            awkwardRate = awkwardRate(current),
            previousAwkwardRate = awkwardRate(previous),
            byCharacter =
                EmotionType.entries.map { emotion ->
                    val verdicts = current.filter { it.emotionType == emotion }
                    CharacterRate(
                        emotion.name,
                        verdicts.size,
                        verdicts.count { it.verdict == ResponseVerdict.AWKWARD },
                        awkwardRate(verdicts),
                    )
                },
            byType =
                AwkwardType.entries
                    .map { type -> TypeCount(type, awkward.count { type in it.awkwardTypes }) }
                    .sortedByDescending { it.count },
            daily =
                current.groupBy { KstDashboardDates.dateOf(it.messageCreatedAt) }.let { byDate ->
                    KstDashboardDates.dateAxis(today, days).map { date ->
                        val verdicts = byDate[date].orEmpty()
                        DailyRate(date, verdicts.size, verdicts.count { it.verdict == ResponseVerdict.AWKWARD }, awkwardRate(verdicts))
                    }
                },
            markers = markers(from, current),
            aiHumanAgreement = calibration.aiHumanAgreement,
            agreementPairs = calibration.comparedPairs,
            judgeModel = calibration.judgeModel,
            judgePromptVersion = calibration.judgePromptVersion,
            judgeCostUsd = (judgeCost * 10000).roundToLong() / 10000.0,
        )
    }

    private fun markers(
        from: Instant,
        verdicts: List<ResponseAiVerdict>,
    ): List<ChangeMarker> {
        // 시스템이 남긴 리비전(마이그레이션 시딩)은 사람이 바꾼 것이 아니라 표시하지 않는다.
        val prompts =
            promptRevisionService
                .findCreatedSince(GENERATION_PROMPTS + PromptType.QUALITY_JUDGE, from)
                .filter { it.savedBy != null }
                .groupBy { KstDashboardDates.dateOf(it.createdAt) to it.promptType }
                .map { (key, revisions) ->
                    val kind = if (key.second == PromptType.QUALITY_JUDGE) MarkerKind.JUDGE else MarkerKind.GENERATION
                    ChangeMarker(key.first, kind, key.second.name, "v${revisions.maxOf { it.version }}")
                }
        val generationModels =
            modelChanges(responseThreadService.generationsSince(from).mapNotNull { g -> g.model?.let { g.createdAt to it } })
                .map { (date, model) -> ChangeMarker(date, MarkerKind.GENERATION, APP_MODEL, model) }
        val judgeModels =
            modelChanges(verdicts.sortedBy { it.createdAt }.map { it.createdAt to it.judgeModel })
                .map { (date, model) -> ChangeMarker(date, MarkerKind.JUDGE, JUDGE_MODEL, model) }
        return (prompts + generationModels + judgeModels).sortedBy { it.date }
    }

    // 처음 보이는 모델은 변경이 아니므로 그 뒤 바뀐 날만 표시한다.
    private fun modelChanges(timeline: List<Pair<Instant, String>>): List<Pair<LocalDate, String>> =
        timeline
            .zipWithNext()
            .filter { (prev, next) -> prev.second != next.second }
            .map { (_, next) -> KstDashboardDates.dateOf(next.first) to next.second }
            .distinct()

    private fun awkwardRate(verdicts: List<ResponseAiVerdict>): Double? =
        CalibrationMetrics.rate(verdicts.count { it.verdict == ResponseVerdict.AWKWARD }, verdicts.size)

    companion object {
        private val GENERATION_PROMPTS = listOf(PromptType.COMMON, PromptType.COMMENT, PromptType.REPLY)
        private const val APP_MODEL = "APP_MODEL"
        private const val JUDGE_MODEL = "JUDGE_MODEL"
    }
}

data class ResponseQualityStats(
    val days: Int,
    val judgedCount: Int,
    val awkwardCount: Int,
    val awkwardRate: Double?,
    val previousAwkwardRate: Double?,
    val byCharacter: List<CharacterRate>,
    val byType: List<TypeCount>,
    val daily: List<DailyRate>,
    val markers: List<ChangeMarker>,
    val aiHumanAgreement: Double?,
    val agreementPairs: Int,
    val judgeModel: String,
    val judgePromptVersion: Int,
    val judgeCostUsd: Double,
)

data class CharacterRate(
    val emotionType: String,
    val judged: Int,
    val awkward: Int,
    val rate: Double?,
)

data class TypeCount(
    val type: AwkwardType,
    val count: Int,
)

data class DailyRate(
    val date: LocalDate,
    val judged: Int,
    val awkward: Int,
    val rate: Double?,
)

enum class MarkerKind {
    GENERATION,
    JUDGE,
}

/** [target]은 바뀐 대상(COMMON, COMMENT, REPLY, QUALITY_JUDGE, APP_MODEL, JUDGE_MODEL), [value]는 바뀐 뒤 값. */
data class ChangeMarker(
    val date: LocalDate,
    val kind: MarkerKind,
    val target: String,
    val value: String,
)
