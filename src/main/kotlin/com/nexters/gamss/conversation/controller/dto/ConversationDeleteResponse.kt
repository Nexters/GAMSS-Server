package com.nexters.gamss.conversation.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 채팅방 일괄 삭제 결과. 클라이언트가 "N개를 삭제했습니다"를 보여줄 수 있게 건수를 돌려준다
 * ([com.nexters.gamss.card.controller.dto.CardDeleteResponse]와 같은 형태).
 */
data class ConversationDeleteResponse(
    @field:Schema(description = "이번 요청으로 삭제된 채팅방 수. 대상이 없으면 0", example = "3")
    val deletedCount: Int,
)
