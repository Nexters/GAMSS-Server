package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.Message

enum class CommentGenerationOutcome {
    GENERATING,
    DONE,
    FAILED,
}

data class CommentGenerationResult(
    val outcome: CommentGenerationOutcome,
    val comments: List<Message> = emptyList(),
)
