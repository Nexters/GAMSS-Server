package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class JudgeSettingRequest(
    @field:NotBlank(message = "model은 필수입니다.")
    @field:Schema(description = "채점 모델(availableModels 중 하나)", example = "gemini-3.1-pro")
    val model: String?,
    @field:NotNull(message = "autoEnabled는 필수입니다.")
    @field:Schema(description = "매일 06:00 자동 채점 여부")
    val autoEnabled: Boolean?,
    @field:NotNull(message = "voteCount는 필수입니다.")
    @field:Min(1, message = "voteCount는 1 이상이어야 합니다.")
    @field:Max(5, message = "voteCount는 5 이하여야 합니다.")
    @field:Schema(description = "응답 하나를 몇 번 채점해 다수결로 정할지(1~5)", example = "3")
    val voteCount: Int?,
)

data class JudgeSettingResponse(
    val model: String,
    val appModel: String,
    val sameAsAppModel: Boolean,
    val availableModels: List<String>,
    val promptVersion: Int?,
    val autoEnabled: Boolean,
    val voteCount: Int,
)
