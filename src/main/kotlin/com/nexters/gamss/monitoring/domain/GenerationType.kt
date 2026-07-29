package com.nexters.gamss.monitoring.domain

/** LLM 생성 종류. 일기 댓글([COMMENT]), 유저 답글 재응답([REPLY]), 대화 종료 시 카드 대사([CARD]). */
enum class GenerationType {
    COMMENT,
    REPLY,
    CARD,
}
