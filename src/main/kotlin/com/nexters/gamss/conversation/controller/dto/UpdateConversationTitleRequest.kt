package com.nexters.gamss.conversation.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 제목 유효성(공백·길이)은 [com.nexters.gamss.conversation.domain.ConversationTitle] 값 객체가 단일 검증한다.
 * 그래서 여기서는 별도 제약을 두지 않는다 — 공백·초과는 INVALID_CONVERSATION_TITLE, 필드 누락은 INVALID_INPUT.
 */
data class UpdateConversationTitleRequest(
    @field:Schema(description = "지정할 채팅방 제목(1~100자)", example = "비 오는 날의 짜증")
    val title: String,
)
