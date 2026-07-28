package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.tokenlimit.domain.TokenPolicy
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/** 유저별 일일 토큰 상한 정책 응답. prod에서만 실제 적용되며, 값·리셋 시각은 백오피스에서 조절한다. */
data class TokenPolicyResponse(
    @field:Schema(description = "유저 1명이 하루에 소비할 수 있는 총 토큰(used_tokens 합)", example = "100000")
    val dailyTokenLimit: Long,
    @field:Schema(description = "일일 사용량이 리셋되는 KST 시각(0~23)", example = "5")
    val resetHour: Int,
    @field:Schema(description = "정책 마지막 수정 시각")
    val updatedAt: Instant,
) {
    companion object {
        fun from(policy: TokenPolicy): TokenPolicyResponse =
            TokenPolicyResponse(
                dailyTokenLimit = policy.dailyTokenLimit,
                resetHour = policy.resetHour,
                updatedAt = policy.updatedAt,
            )
    }
}
