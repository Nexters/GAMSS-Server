package com.nexters.gamss.admin.service

import com.nexters.gamss.emotion.domain.EmotionType

/** 감정 하나의 카드 수(대시보드 감정 분포 한 조각). */
data class EmotionCount(
    val emotion: EmotionType,
    val count: Long,
)
