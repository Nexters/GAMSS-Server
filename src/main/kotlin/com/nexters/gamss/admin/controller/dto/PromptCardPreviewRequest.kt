package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.emotion.domain.EmotionType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** 공통 프롬프트를 받지 않는 것은 카드 프롬프트가 조립되지 않기 때문이다(단독 사용). */
data class PromptCardPreviewRequest(
    @field:Schema(description = "시험할 카드 프롬프트(미저장 원본). null이면 저장된 현재값 사용", nullable = true)
    @field:Size(max = 20_000, message = "cardPrompt는 20000자 이하여야 합니다.")
    val cardPrompt: String? = null,
    @field:Schema(description = "대표 감정(어떤 사건을 고를지의 기준)", example = "ANGER")
    @field:NotNull(message = "emotion은 필수입니다.")
    val emotion: EmotionType?,
    @field:Schema(
        description = "다듬을 대화 요약(클라이언트가 보내는 값과 같은 성격)",
        example = "오늘 팀장이 자기 할 일을 다 떠넘김. 야근함.",
    )
    @field:NotBlank(message = "summary는 필수입니다.")
    @field:Size(max = 2000, message = "summary는 2000자 이하여야 합니다.")
    val summary: String,
)
