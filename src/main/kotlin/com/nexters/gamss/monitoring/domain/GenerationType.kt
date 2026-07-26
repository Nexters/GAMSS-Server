package com.nexters.gamss.monitoring.domain

/** LLM 생성 종류. 일기에 대한 캐릭터 댓글([COMMENT]) 또는 유저 답글에 대한 재응답([REPLY]). */
enum class GenerationType {
    COMMENT,
    REPLY,
}
