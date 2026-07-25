package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.llm.prompt.PromptType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class UpdateLlmSettingsRequest(
    @field:NotNull(message = "promptType은 필수입니다.")
    @field:Schema(description = "수정할 프롬프트 타입", example = "COMMENT", allowableValues = ["COMMON", "COMMENT", "REPLY", "CARD"])
    val promptType: PromptType?,
    @field:Schema(description = "적용할 모델(availableModels 중 하나). COMMON 타입은 모델이 없으므로 생략 가능", example = "gemini-3.1-flash-lite")
    val model: String?,
    @field:NotBlank(message = "systemPrompt는 필수입니다.")
    @field:Schema(description = "적용할 시스템 프롬프트")
    val systemPrompt: String,
)
