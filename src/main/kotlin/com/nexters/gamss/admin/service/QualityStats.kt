package com.nexters.gamss.admin.service

/**
 * 대시보드 'LLM 품질 / 안정성' 집계 결과. 최근 기간의 생성 성공률·호출·재시도·지연·토큰 + 막힌 PENDING.
 *
 * 응답 포맷(필드명·Swagger 스키마)은 [com.nexters.gamss.admin.controller.dto.QualityStatsResponse] 가
 * 맡는다. 여기는 무엇을 집계했는지만 담는다.
 */
data class QualityStats(
    val totalGenerations: Long,
    val successGenerations: Long,
    val failedGenerations: Long,
    /** 성공률(%). 생성 요청이 없으면 null. */
    val successRate: Double?,
    val totalLlmCalls: Long,
    /** 재시도율(%). 생성 요청이 없으면 null. */
    val retryRate: Double?,
    val avgLatencyMs: Long,
    val p95LatencyMs: Long,
    val totalTokens: Long,
    val cachedTokens: Long,
    /** 캐시 적중률(%). 입력 토큰이 0이면 null. */
    val cacheHitRate: Double?,
    val estimatedCostUsd: Double,
    /** 막힌 PENDING(고아 생성) 수. 즉시 대응이 필요한 신호다. */
    val stuckPending: Long,
    /** 오래된 날부터 오늘까지. */
    val dailyGeneration: List<DailyGeneration>,
)
