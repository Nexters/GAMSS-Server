package com.nexters.gamss.conversation.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

data class GenerateCommentsRequest(
    @field:NotNull(message = "messageId는 필수입니다.")
    @field:Schema(description = "댓글을 생성할 일기(사용자) 메시지 ID", example = "1")
    val messageId: Long?,
)
