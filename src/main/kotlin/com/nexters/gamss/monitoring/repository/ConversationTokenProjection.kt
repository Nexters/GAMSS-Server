package com.nexters.gamss.monitoring.repository

/** 대화방별 토큰 합계 집계 결과(백오피스 대화방 사용량). Spring Data 인터페이스 프로젝션. */
interface ConversationTokenProjection {
    val conversationId: Long
    val totalTokens: Long
    val cachedTokens: Long
}
