package com.nexters.gamss.card.controller.dto

import com.nexters.gamss.emotion.domain.EmotionType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateCardRequest(
    @field:NotNull(message = "conversationId는 필수입니다.")
    @field:Schema(description = "카드를 만들 종료된 채팅방 ID", example = "1")
    val conversationId: Long?,
    @field:NotNull(message = "emotion은 필수입니다.")
    @field:Schema(description = "대화를 대표하는 감정(그대로 카드 대표 감정이 됨)", example = "ANGER")
    val emotion: EmotionType?,
    @field:NotBlank(message = "summary는 필수입니다.")
    @field:Size(max = 1000, message = "summary는 1000자 이하여야 합니다.")
    @field:Schema(description = "클라이언트가 만든 대화 요약(카드 제목으로 저장됨)", example = "오늘 비가 와서 짜증나고 찝찝하다")
    val summary: String?,
)
