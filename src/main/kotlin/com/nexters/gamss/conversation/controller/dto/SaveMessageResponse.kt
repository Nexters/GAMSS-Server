package com.nexters.gamss.conversation.controller.dto

import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.service.CommentGenerationResult
import com.nexters.gamss.conversation.service.ReplyGenerationResult
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 저장한 메시지와 그 자리에서 동기로 생성한 캐릭터 댓글/답글을 함께 담는다. 일기(comments 여러 개)와
 * 답글(comments 0/1개) 두 흐름을 같은 필드로 통일한다 — 저장이 방금 새로 만든 메시지 행을 대상으로만
 * 생성을 시도하므로 GENERATING은 나오지 않는다(status는 항상 DONE 또는 FAILED).
 */
data class SaveMessageResponse(
    @field:Schema(description = "저장된 메시지")
    val message: MessageResponse,
    @field:Schema(description = "댓글 생성 상태 (저장과 동기로 처리되어 GENERATING은 나오지 않는다)")
    val commentStatus: CommentGenerationStatus,
    @field:Schema(description = "생성된 캐릭터 댓글·티키타카(일기) 또는 재응답(답글). commentStatus=FAILED면 빈 리스트")
    val comments: List<MessageResponse>,
    @field:Schema(description = "이번 요청에서 LLM을 호출해 생성한 경우에만 채워지는 사용 토큰 수", nullable = true)
    val usedTokens: Int? = null,
) {
    companion object {
        fun from(
            message: Message,
            result: CommentGenerationResult,
        ): SaveMessageResponse =
            SaveMessageResponse(
                message = MessageResponse.from(message),
                commentStatus = result.outcome.toResponseStatus(),
                comments = result.comments.map { MessageResponse.from(it) },
                usedTokens = result.usedTokens,
            )

        fun from(
            message: Message,
            result: ReplyGenerationResult,
        ): SaveMessageResponse =
            SaveMessageResponse(
                message = MessageResponse.from(message),
                commentStatus = result.outcome.toResponseStatus(),
                comments = listOfNotNull(result.message).map { MessageResponse.from(it) },
                usedTokens = result.usedTokens,
            )
    }
}
