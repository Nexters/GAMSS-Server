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
    @field:Schema(description = "카드 제목(클라가 만든 요약)", example = "오늘 비가 와서 짜증나고 찝찝하다")
    val summary: String,
    @field:Schema(description = "대표 감정 캐릭터의 한 줄 대사", example = "얘 오늘 건들면 안 됨.")
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
