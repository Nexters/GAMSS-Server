package com.nexters.gamss.conversation.controller.dto

import com.nexters.gamss.emotion.domain.EmotionType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SaveMessageRequest(
    @field:Schema(description = "이어서 쓸 채팅방 ID (없으면 새 채팅방 생성)", example = "1", nullable = true)
    val conversationId: Long? = null,
    @field:NotBlank(message = "content는 필수입니다.")
    @field:Size(max = 140, message = "content는 140자 이하여야 합니다.")
    @field:Schema(description = "감정 기록 내용 (최대 140자)", example = "오늘 억울한 일이 있었어")
    val content: String,
    @field:Schema(description = "답장 대상 메시지 ID (답장이 아니면 생략)", example = "3", nullable = true)
    val repliesToMessageId: Long? = null,
    @field:Size(max = 2_000, message = "currentConversationSummary는 2000자 이하여야 합니다.")
    @field:Schema(
        description =
            "현재 채팅방 전체를 프론트가 압축한 요약(최대 2000자). 댓글 생성 컨텍스트로 쓰이며, " +
                "마지막으로 보낸 값이 저장되어 자동 종료 배치가 카드 요약으로 사용합니다 — " +
                "보내지 않으면 그 대화방은 자동 종료 시 카드가 생성되지 않습니다.",
        example = "아침에 커피 쏟음. 회사 지각함. 회사에서 바빴음.",
        nullable = true,
    )
    val currentConversationSummary: String? = null,
    @field:Schema(
        description =
            "새 채팅방을 만들 때 반응하지 않길 원하는 캐릭터 목록(선택, 최대 5종 — 전체 제외는 불가). " +
                "conversationId가 있으면(기존 채팅방에 이어서 보내는 요청) 무시됩니다.",
        example = "[\"ANGER\", \"ANXIETY\"]",
        nullable = true,
    )
    val excludeCharacters: List<EmotionType>? = null,
)
