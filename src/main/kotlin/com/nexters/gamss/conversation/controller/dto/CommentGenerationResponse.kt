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
    @field:Schema(
        description = "이번 요청에서 실제로 LLM을 호출해 생성한 경우에만 채워지는 사용 토큰 수 (이미 생성된 결과를 재조회한 경우 등은 null)",
        nullable = true,
    )
    val usedTokens: Int? = null,
)
