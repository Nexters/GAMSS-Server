package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.admin.service.QualityStats
import io.swagger.v3.oas.annotations.media.Schema

/** 대시보드 'LLM 품질 / 안정성' 섹션 응답. 최근 기간의 생성 성공률·호출·재시도·지연·토큰 + 막힌 PENDING. */
data class QualityStatsResponse(
    @field:Schema(description = "총 생성 요청 수(최근 기간)", example = "312")
    val totalGenerations: Long,
    @field:Schema(description = "성공한 생성 수", example = "305")
    val successGenerations: Long,
    @field:Schema(description = "실패한 생성 수", example = "7")
    val failedGenerations: Long,
    @field:Schema(description = "성공률(%). 생성 요청이 없으면 null", example = "97.8", nullable = true)
    val successRate: Double?,
    @field:Schema(description = "실제 LLM 호출 수(재시도 포함)", example = "334")
    val totalLlmCalls: Long,
    @field:Schema(description = "재시도율(%). 2회 이상 호출된 생성 비율. 요청이 없으면 null", example = "6.4", nullable = true)
    val retryRate: Double?,
    @field:Schema(description = "평균 생성 지연(ms)", example = "2140")
    val avgLatencyMs: Long,
    @field:Schema(description = "p95 생성 지연(ms)", example = "5300")
    val p95LatencyMs: Long,
    @field:Schema(description = "조회 기간 내 누적 토큰 사용량(총합, 캐싱과 무관하게 처리된 전체 토큰)", example = "184320")
    val totalTokens: Long,
    @field:Schema(description = "그중 컨텍스트 캐시로 처리돼 할인 과금된 토큰", example = "120500")
    val cachedTokens: Long,
    @field:Schema(description = "캐시 적중률(%). 입력 토큰 대비 캐시 토큰 비율(출력은 캐시 대상 아님). 입력이 0이면 null", example = "65.4", nullable = true)
    val cacheHitRate: Double?,
    @field:Schema(description = "예상 비용(USD). 모델별 요금표로 입력·캐시입력·출력을 각각 계산한 합계", example = "0.4213")
    val estimatedCostUsd: Double,
    @field:Schema(description = "막힌 PENDING(고아 생성) 수 — 즉시 대응 필요 신호", example = "0")
    val stuckPending: Long,
    @field:Schema(description = "일별 생성 성공/실패 추이(오래된 날 → 오늘)")
    val dailyGeneration: List<DailyGenerationResponse>,
) {
    companion object {
        fun from(stats: QualityStats): QualityStatsResponse =
            QualityStatsResponse(
                totalGenerations = stats.totalGenerations,
                successGenerations = stats.successGenerations,
                failedGenerations = stats.failedGenerations,
                successRate = stats.successRate,
                totalLlmCalls = stats.totalLlmCalls,
                retryRate = stats.retryRate,
                avgLatencyMs = stats.avgLatencyMs,
                p95LatencyMs = stats.p95LatencyMs,
                totalTokens = stats.totalTokens,
                cachedTokens = stats.cachedTokens,
                cacheHitRate = stats.cacheHitRate,
                estimatedCostUsd = stats.estimatedCostUsd,
                stuckPending = stats.stuckPending,
                dailyGeneration = stats.dailyGeneration.map(DailyGenerationResponse::from),
            )
    }
}
