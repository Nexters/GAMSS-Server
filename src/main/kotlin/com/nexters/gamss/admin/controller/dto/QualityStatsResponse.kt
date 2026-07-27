package com.nexters.gamss.admin.controller.dto

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
    @field:Schema(description = "누적 토큰 사용량(최근 기간)", example = "184320")
    val totalTokens: Long,
    @field:Schema(description = "막힌 PENDING(고아 생성) 수 — 즉시 대응 필요 신호", example = "0")
    val stuckPending: Long,
    @field:Schema(description = "일별 생성 성공/실패 추이(오래된 날 → 오늘)")
    val dailyGeneration: List<DailyGenerationResponse>,
)
