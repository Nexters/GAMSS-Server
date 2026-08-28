package com.nexters.gamss.member.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

/** 본인의 오늘(KST) 토큰 사용량. [dailyLimit]이 null이면 상한 미적용(무제한) 환경이다. */
data class TokenUsageResponse(
    @field:Schema(description = "오늘 사용한 토큰 수", example = "12000")
    val usedTokens: Long,
    @field:Schema(description = "일일 토큰 상한 (상한 미적용 환경이면 null)", example = "100000", nullable = true)
    val dailyLimit: Long?,
    @field:Schema(description = "상한 초과 여부 (상한 미적용 환경이면 항상 false)", example = "false")
    val exceeded: Boolean,
)
