package com.nexters.gamss.conversation.service

enum class CommentGenerationOutcome {
    GENERATING,
    DONE,
    FAILED,

    /** 일일 토큰과 유료 토큰을 모두 써서 생성을 건너뜀. 메시지 저장 자체는 유지된다(저장 O, 생성만 차단). */
    LIMIT_EXCEEDED,
}
