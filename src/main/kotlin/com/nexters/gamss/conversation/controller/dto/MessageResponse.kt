package com.nexters.gamss.conversation.controller.dto

import com.nexters.gamss.conversation.domain.Message
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class MessageResponse(
    @field:Schema(description = "메시지 ID", example = "1")
    val id: Long,
    @field:Schema(description = "채팅방 ID", example = "1")
    val conversationId: Long,
    @field:Schema(description = "발신 주체", example = "USER", allowableValues = ["USER", "CHARACTER"])
    val senderType: String,
    @field:Schema(
        description = "감정 캐릭터 (캐릭터 메시지에만 존재). WARM(다정)은 신규 생성에서 빠졌지만 과거 메시지에는 남아 있음",
        example = "JOY",
        allowableValues = ["JOY", "SADNESS", "ANGER", "ANXIETY", "GRUMPY", "QUIRKY", "WARM"],
        nullable = true,
    )
    val emotionType: String?,
    @field:Schema(description = "메시지 내용", example = "오늘 억울한 일이 있었어")
    val content: String,
    @field:Schema(description = "답장 대상 메시지 ID (답장이 아니면 null)", example = "3", nullable = true)
    val repliesToMessageId: Long?,
    @field:Schema(
        description = "이 메시지가 속한 원본 일기 메시지 ID (캐릭터 댓글·티키타카에만 존재)",
        example = "1",
        nullable = true,
    )
    val rootMessageId: Long?,
    @field:Schema(description = "작성 일시")
    val createdAt: Instant,
) {
    companion object {
        fun from(message: Message): MessageResponse =
            MessageResponse(
                id = message.id,
                conversationId = message.conversationId,
                senderType = message.senderType.name,
                emotionType = message.emotionType?.name,
                content = message.content,
                repliesToMessageId = message.repliesToMessageId,
                rootMessageId = message.rootMessageId,
                createdAt = message.createdAt,
            )
    }
}
