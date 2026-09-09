package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.llm.provider.LlmProvider
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

data class UpdateLlmProviderRequest(
    @field:NotNull(message = "provider는 필수입니다.")
    @field:Schema(description = "전환할 호출 경로(availableProviders 중 하나)", example = "VERTEX_AI")
    val provider: LlmProvider?,
)
