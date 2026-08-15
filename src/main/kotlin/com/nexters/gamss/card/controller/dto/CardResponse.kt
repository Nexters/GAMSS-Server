package com.nexters.gamss.card.controller.dto

import com.nexters.gamss.card.domain.Card
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.ZoneId

data class CardResponse(
    @field:Schema(description = "카드 ID", example = "1")
    val id: Long,
    @field:Schema(description = "카드가 만들어진 채팅방 ID", example = "1")
    val conversationId: Long,
    @field:Schema(description = "대표 감정", example = "ANGER")
    val emotion: String,
    @field:Schema(description = "대표 감정 한글 라벨", example = "분노")
    val emotionLabel: String,
    @field:Schema(
        description = "카드에 남는 한 줄 — 그날 있었던 일을 서버 LLM이 다듬은 요약(공백 포함 50자 이하)",
        example = "우산을 안 챙겨서 옷이 다 젖어버렸어요",
    )
    val summary: String,
    @field:Schema(
        description = "summary와 같은 값. 카드에 캐릭터 대사를 싣던 시절의 필드이며 더 이상 쓰지 않는다(deprecated)",
        example = "우산을 안 챙겨서 옷이 다 젖어버렸어요",
        deprecated = true,
    )
    val message: String,
    @field:Schema(description = "카드가 속한 날짜(대화 생성일 기준, KST)", example = "2026-07-23")
    val date: LocalDate,
) {
    companion object {
        private val ZONE = ZoneId.of("Asia/Seoul")

        fun from(card: Card): CardResponse =
            CardResponse(
                id = card.id,
                conversationId = card.conversationId,
                emotion = card.emotion.name,
                emotionLabel = card.emotion.label,
                summary = card.summary,
                message = card.message,
                date = card.conversationCreatedAt.atZone(ZONE).toLocalDate(),
            )
    }
}
