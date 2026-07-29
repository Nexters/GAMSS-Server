package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class UpdateTokenPolicyRequest(
    @field:NotNull(message = "dailyTokenLimit은 필수입니다.")
    @field:Min(value = 0, message = "일일 토큰 상한은 0 이상이어야 합니다.")
    @field:Schema(description = "유저 1명이 하루에 소비할 수 있는 총 토큰", example = "100000")
    val dailyTokenLimit: Long?,
    @field:NotNull(message = "resetHour는 필수입니다.")
    @field:Min(value = 0, message = "리셋 시각은 0~23 사이여야 합니다.")
    @field:Max(value = 23, message = "리셋 시각은 0~23 사이여야 합니다.")
    @field:Schema(description = "일일 사용량이 리셋되는 KST 시각(0~23)", example = "5")
    val resetHour: Int?,
)
