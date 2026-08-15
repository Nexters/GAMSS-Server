package com.nexters.gamss.conversation.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class DeleteConversationsRequest(
    @field:NotEmpty(message = "conversationIds는 1개 이상이어야 합니다.")
    @field:Size(max = MAX_IDS, message = "conversationIds는 ${MAX_IDS}개 이하여야 합니다.")
    @field:Schema(description = "삭제할 채팅방 ID 목록 (1~${MAX_IDS}개)", example = "[1, 2, 3]")
    val conversationIds: List<Long>,
) {
    companion object {
        /** 한 요청에서 지울 수 있는 최대 개수. 채팅방 검색의 페이지 상한과 같은 값으로 맞춘다. */
        const val MAX_IDS = 100
    }
}
