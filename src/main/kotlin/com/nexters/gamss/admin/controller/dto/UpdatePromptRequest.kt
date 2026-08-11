package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.llm.prompt.PromptType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class UpdatePromptRequest(
    @field:NotNull(message = "promptType은 필수입니다.")
    @field:Schema(
        description = "수정할 프롬프트 타입",
        example = "COMMENT",
        allowableValues = ["COMMON", "COMMENT", "REPLY", "CARD", "EONGTTUNG_TOPIC"],
    )
    val promptType: PromptType?,
    @field:NotBlank(message = "systemPrompt는 필수입니다.")
    @field:Schema(description = "적용할 시스템 프롬프트")
    val systemPrompt: String,
)
