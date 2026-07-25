package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class UpdateModelRequest(
    @field:NotBlank(message = "model은 필수입니다.")
    @field:Schema(description = "적용할 모델(availableModels 중 하나)", example = "gemini-3.1-flash-lite")
    val model: String?,
)
