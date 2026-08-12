package com.nexters.gamss.monitoring.domain

/**
 * LLM 생성 종류. 일기 댓글([COMMENT]), 유저 답글 재응답([REPLY]), 대화 종료 시 카드 대사([CARD]),
 * 백오피스 플레이그라운드 실험([PREVIEW] - 비용 추적용으로만 기록하며 품질 지표 집계에서 제외).
 */
enum class GenerationType {
    COMMENT,
    REPLY,
    CARD,
    PREVIEW,
}
