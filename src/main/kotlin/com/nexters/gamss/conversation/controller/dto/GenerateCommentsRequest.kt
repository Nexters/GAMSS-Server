package com.nexters.gamss.conversation.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class GenerateCommentsRequest(
    @field:NotNull(message = "messageId는 필수입니다.")
    @field:Schema(description = "댓글을 생성할 일기(사용자) 메시지 ID", example = "1")
    val messageId: Long?,
    @field:Size(max = 2_000, message = "currentConversationSummary는 2000자 이하여야 합니다.")
    @field:Schema(
        description =
            "현재 채팅방 전체를 프론트가 압축한 임시 요약(저장하지 않고 생성 컨텍스트로만 사용, 최대 2000자). " +
                "생략하면 맥락 없이 재생성한다.",
        example = "아침에 커피 쏟음. 회사 지각함. 회사에서 바빴음.",
        nullable = true,
    )
    val currentConversationSummary: String? = null,
)
