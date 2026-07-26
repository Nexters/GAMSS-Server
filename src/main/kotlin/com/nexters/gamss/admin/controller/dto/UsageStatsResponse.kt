package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

/** 대시보드 '사용량 / 도입' 섹션 응답. 오늘 KPI + 활동 회원 + 감정 분포 + 일별 추이. */
data class UsageStatsResponse(
    @field:Schema(description = "오늘 시작된 대화 수", example = "8")
    val todayConversations: Long,
    @field:Schema(description = "오늘 작성된 메시지 수(유저)", example = "37")
    val todayUserMessages: Long,
    @field:Schema(description = "오늘 작성된 메시지 수(캐릭터)", example = "112")
    val todayCharacterMessages: Long,
    @field:Schema(description = "오늘 생성된 카드 수", example = "5")
    val todayCards: Long,
    @field:Schema(description = "오늘 신규 가입 수", example = "3")
    val todaySignups: Long,
    @field:Schema(description = "오늘 활동 회원 수(DAU, 메시지 작성 기준)", example = "21")
    val dau: Long,
    @field:Schema(description = "최근 7일 활동 회원 수(WAU)", example = "84")
    val wau: Long,
    @field:Schema(description = "감정 분포(최근 기간 카드 기준)")
    val emotionDistribution: List<EmotionCountResponse>,
    @field:Schema(description = "일별 활동 추이(오래된 날 → 오늘)")
    val dailyActivity: List<DailyActivityResponse>,
)
