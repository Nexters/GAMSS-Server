package com.nexters.gamss.conversation.controller.dto

import com.nexters.gamss.conversation.service.ConversationDetail
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 채팅방 상세. 대화방 정보와 메시지를 한 번에 내려준다.
 *
 * 대화방 부분을 펼치지 않고 [ConversationResponse]를 그대로 감싼다 — 펼치면 대화방 필드를 여기에
 * 복사하게 되고, 그때부터 목록·종료·제목수정·삭제 응답과 따로 놀기 시작한다(필드를 추가할 때 한쪽만
 * 고치는 실수). 감싸두면 대화방은 어디서 내려가든 같은 모양이다.
 */
data class ConversationDetailResponse(
    @field:Schema(description = "대화방 정보")
    val conversation: ConversationResponse,
    @field:Schema(description = "이 대화방의 메시지 전체(시간순, 티키타카는 답장 대상 댓글 바로 다음에 배치)")
    val messages: List<MessageResponse>,
) {
    companion object {
        fun from(detail: ConversationDetail): ConversationDetailResponse =
            ConversationDetailResponse(
                conversation = ConversationResponse.from(detail.conversation),
                messages = detail.messages.map { MessageResponse.from(it) },
            )
    }
}
