package com.nexters.gamss.admin.service

/**
 * 대시보드 '사용량 / 도입' 집계 결과. 오늘 KPI + 활동 회원 + 감정 분포 + 일별 추이.
 *
 * 응답 포맷(필드명·Swagger 스키마)은 [com.nexters.gamss.admin.controller.dto.UsageStatsResponse] 가
 * 맡는다. 여기는 무엇을 집계했는지만 담는다.
 */
data class UsageStats(
    val todayConversations: Long,
    val todayUserMessages: Long,
    /** 활동 유저 1명당 평균 유저 메시지 수. 활동 유저가 없으면 무의미하므로 null. */
    val avgMessagesPerUser: Double?,
    val todayCards: Long,
    val todaySignups: Long,
    val dau: Long,
    val wau: Long,
    val emotionDistribution: List<EmotionCount>,
    /** 오래된 날부터 오늘까지. */
    val dailyActivity: List<DailyActivity>,
)
