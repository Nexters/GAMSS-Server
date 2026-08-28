package com.nexters.gamss.admin.service

import java.time.LocalDate

/** 하루치 LLM 생성 성공·실패 수. 대시보드 품질 추이의 데이터 포인트. */
data class DailyGeneration(
    val date: LocalDate,
    val success: Long,
    val failed: Long,
)
