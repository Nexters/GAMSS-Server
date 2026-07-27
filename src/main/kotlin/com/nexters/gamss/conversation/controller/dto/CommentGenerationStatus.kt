package com.nexters.gamss.conversation.controller.dto

import com.nexters.gamss.conversation.service.CommentGenerationOutcome

enum class CommentGenerationStatus {
    GENERATING,
    DONE,
    FAILED,
}

fun CommentGenerationOutcome.toResponseStatus(): CommentGenerationStatus =
    when (this) {
        CommentGenerationOutcome.GENERATING -> CommentGenerationStatus.GENERATING
        CommentGenerationOutcome.DONE -> CommentGenerationStatus.DONE
        CommentGenerationOutcome.FAILED -> CommentGenerationStatus.FAILED
    }
