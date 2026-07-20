package com.nexters.gamss.conversation.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

enum class CommentGenerationStatus {
    GENERATING,
    DONE,
    FAILED,
}

data class CommentGenerationResponse(
    @field:Schema(description = "댓글 생성 상태. 재요청 시 결과 조회를 겸한다")
    val status: CommentGenerationStatus,
    @field:Schema(description = "생성된 캐릭터 댓글·티키타카 (status=DONE일 때만 채워짐)", nullable = true)
    val comments: List<MessageResponse>? = null,
)
