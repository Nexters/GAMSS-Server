package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

/** 하루치 활동량(대화·메시지·카드 생성 수). 대시보드 사용량 추이 차트의 데이터 포인트. */
data class DailyActivityResponse(
    @field:Schema(description = "날짜(KST)", example = "2026-07-26")
    val date: LocalDate,
    @field:Schema(description = "그날 시작된 대화 수", example = "8")
    val conversations: Long,
    @field:Schema(description = "그날 작성된 메시지 수(유저+캐릭터)", example = "142")
    val messages: Long,
    @field:Schema(description = "그날 생성된 카드 수", example = "5")
    val cards: Long,
)
