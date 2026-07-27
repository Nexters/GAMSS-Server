package com.nexters.gamss.card.repository

import com.nexters.gamss.emotion.domain.EmotionType

/** 감정별 카드 수 집계 결과(대시보드 감정 분포). Spring Data 인터페이스 프로젝션. */
interface EmotionCountProjection {
    val emotion: EmotionType
    val count: Long
}
