package com.nexters.gamss.admin.service

import com.nexters.gamss.admin.controller.dto.DailyGenerationResponse
import com.nexters.gamss.admin.controller.dto.QualityStatsResponse
import com.nexters.gamss.conversation.config.ConversationProperties
import com.nexters.gamss.conversation.domain.CommentStatus
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.monitoring.domain.GenerationLog
import com.nexters.gamss.monitoring.repository.GenerationLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * 대시보드 'LLM 품질 / 안정성' 집계(SRP: 품질 지표만 담당). 최근 기간 생성 로그를 한 번 읽어
 * 성공률·호출·재시도·지연·토큰을 앱에서 집계하고, 막힌 PENDING은 comment_status로 별도 확인한다.
 */
@Service
class QualityStatsService(
    private val generationLogRepository: GenerationLogRepository,
    private val messageRepository: MessageRepository,
    private val conversationProperties: ConversationProperties,
) {
    @Transactional(readOnly = true)
    fun getQualityStats(days: Int): QualityStatsResponse {
        val today = KstDashboardDates.today()
        val since = KstDashboardDates.daysAgoStart(today, days)
        val logs = generationLogRepository.findAllSince(since)

        val total = logs.size.toLong()
        val success = logs.count { it.success }.toLong()
        val retried = logs.count { it.attemptCount > 1 }.toLong()
        val latencies = logs.map { it.latencyMs }.sorted()
        val stuckBefore = Instant.now().minus(conversationProperties.commentPendingTimeout)

        return QualityStatsResponse(
            totalGenerations = total,
            successGenerations = success,
            failedGenerations = total - success,
            successRate = percentageOrNull(success, total),
            totalLlmCalls = logs.sumOf { it.attemptCount.toLong() },
            retryRate = percentageOrNull(retried, total),
            avgLatencyMs = if (latencies.isEmpty()) 0 else latencies.average().roundToLong(),
            p95LatencyMs = percentile(latencies, P95),
            totalTokens = logs.sumOf { (it.usedTokens ?: 0).toLong() },
            stuckPending = messageRepository.countByCommentStatusOlderThan(CommentStatus.PENDING, stuckBefore),
            dailyGeneration = buildDailyGeneration(today, days, logs),
        )
    }

    private fun buildDailyGeneration(
        today: LocalDate,
        days: Int,
        logs: List<GenerationLog>,
    ): List<DailyGenerationResponse> {
        val logsByDate = logs.groupBy { KstDashboardDates.dateOf(it.createdAt) }
        return KstDashboardDates.dateAxis(today, days).map { date ->
            val dayLogs = logsByDate[date].orEmpty()
            DailyGenerationResponse(
                date = date,
                success = dayLogs.count { it.success }.toLong(),
                failed = dayLogs.count { !it.success }.toLong(),
            )
        }
    }

    /** [count]가 [total]에서 차지하는 비율(%). total 이 0이면 무의미하므로 null. 소수 첫째 자리 반올림. */
    private fun percentageOrNull(
        count: Long,
        total: Long,
    ): Double? {
        if (total == 0L) {
            return null
        }
        return (count * 1000.0 / total).roundToLong() / 10.0
    }

    /** 오름차순 정렬된 [sorted]의 [p] 백분위수. 비어 있으면 0. */
    private fun percentile(
        sorted: List<Long>,
        p: Int,
    ): Long {
        if (sorted.isEmpty()) {
            return 0
        }
        val index = (ceil(p / 100.0 * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    companion object {
        private const val P95 = 95
    }
}
