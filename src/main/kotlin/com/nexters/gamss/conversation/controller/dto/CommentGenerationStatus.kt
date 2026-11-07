package com.nexters.gamss.conversation.controller.dto

import com.nexters.gamss.conversation.service.CommentGenerationOutcome

enum class CommentGenerationStatus {
    GENERATING,
    DONE,
    FAILED,

    /** 일일 토큰과 유료 토큰을 모두 써서 생성을 건너뜀(저장은 유지). 클라이언트는 안내 후 다음 리셋 뒤 또는 토큰 구매 뒤 재시도 가능. */
    LIMIT_EXCEEDED,
}

fun CommentGenerationOutcome.toResponseStatus(): CommentGenerationStatus =
    when (this) {
        CommentGenerationOutcome.GENERATING -> CommentGenerationStatus.GENERATING
        CommentGenerationOutcome.DONE -> CommentGenerationStatus.DONE
        CommentGenerationOutcome.FAILED -> CommentGenerationStatus.FAILED
        CommentGenerationOutcome.LIMIT_EXCEEDED -> CommentGenerationStatus.LIMIT_EXCEEDED
    }
