package com.nexters.gamss.admin.service

import java.time.LocalDate

/** 하루치 활동량(대화·메시지·카드 생성 수). 대시보드 사용량 추이의 데이터 포인트. */
data class DailyActivity(
    val date: LocalDate,
    val conversations: Long,
    val messages: Long,
    val cards: Long,
)
