package com.nexters.gamss.conversation.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class UpdateConversationTitleRequest(
    @field:Schema(description = "지정할 채팅방 제목(1~100자)", example = "비 오는 날의 짜증")
    @field:NotBlank(message = "제목을 입력해 주세요.")
    val title: String,
)
