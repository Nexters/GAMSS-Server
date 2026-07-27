package com.nexters.gamss.conversation.controller.dto

import com.nexters.gamss.conversation.domain.Conversation
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class ConversationResponse(
    @field:Schema(description = "채팅방 ID", example = "1")
    val id: Long,
    @field:Schema(description = "채팅방 제목. 아직 지정하지 않았으면 null", example = "비 오는 날의 짜증", nullable = true)
    val title: String?,
    @field:Schema(description = "채팅방 상태", example = "ACTIVE", allowableValues = ["ACTIVE", "ENDED", "DELETED"])
    val status: String,
    @field:Schema(description = "생성 일시")
    val createdAt: Instant,
    @field:Schema(description = "수정 일시")
    val updatedAt: Instant,
) {
    companion object {
        fun from(conversation: Conversation): ConversationResponse =
            ConversationResponse(
                id = conversation.id,
                title = conversation.title?.value,
                status = conversation.status.name,
                createdAt = conversation.createdAt,
                updatedAt = conversation.updatedAt,
            )
    }
}
