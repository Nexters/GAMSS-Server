package com.nexters.gamss.conversation.controller.dto

import com.nexters.gamss.conversation.search.ConversationSearchResult
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class ConversationSearchResponse(
    @field:Schema(description = "채팅방 ID", example = "1")
    val conversationId: Long,
    @field:Schema(description = "채팅방 제목(= 카드 요약). 카드가 없는 진행 중 대화방은 null", example = "비 오는 날의 짜증", nullable = true)
    val title: String?,
    @field:Schema(description = "채팅방 상태", example = "ENDED", allowableValues = ["ACTIVE", "ENDED"])
    val status: String,
    @field:Schema(description = "생성 일시")
    val createdAt: Instant,
) {
    companion object {
        fun from(result: ConversationSearchResult): ConversationSearchResponse =
            ConversationSearchResponse(
                conversationId = result.conversationId,
                title = result.title,
                status = result.status.name,
                createdAt = result.createdAt,
            )
    }
}
