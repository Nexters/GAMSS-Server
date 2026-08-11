package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.llm.prompt.PromptType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class UpdatePromptRequest(
    @field:NotNull(message = "promptType은 필수입니다.")
    @field:Schema(description = "수정할 프롬프트 타입", example = "COMMENT", allowableValues = ["COMMON", "COMMENT", "REPLY", "CARD"])
    val promptType: PromptType?,
    @field:NotBlank(message = "systemPrompt는 필수입니다.")
    // 미리보기 요청 DTO와 같은 상한. 규칙이 갈라지면 "저장은 되는데 미리보기는 거부"가 생긴다.
    @field:Size(max = 20_000, message = "systemPrompt는 20000자 이하여야 합니다.")
    @field:Schema(description = "적용할 시스템 프롬프트")
    val systemPrompt: String,
)
