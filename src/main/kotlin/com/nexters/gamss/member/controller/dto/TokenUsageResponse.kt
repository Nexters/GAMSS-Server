package com.nexters.gamss.member.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 본인의 오늘(KST, 정책 리셋 시각 기준) 토큰 사용량과 남은 유료 토큰. [dailyLimit]이 null이면 상한 미적용(무제한) 환경이다.
 * [paidBalance]는 리셋과 상관없는 누적 잔액이다.
 */
data class TokenUsageResponse(
    @field:Schema(
        description = "오늘 댓글·답글 생성에 쓴 토큰 수. 일일 토큰을 다 쓴 뒤 유료 토큰으로 쓴 몫도 들어가므로 dailyLimit보다 클 수 있다",
        example = "12000",
    )
    val usedTokens: Long,
    @field:Schema(description = "일일 토큰 상한 (상한 미적용 환경이면 null)", example = "100000", nullable = true)
    val dailyLimit: Long?,
    @field:Schema(
        description = "댓글·답글 생성이 막혔는지 여부. 일일 토큰을 다 썼고 유료 잔액도 0 이하이면 true (상한 미적용 환경이면 항상 false)",
        example = "false",
    )
    val exceeded: Boolean,
    @field:Schema(description = "남은 유료 토큰. 리셋과 상관없는 누적 잔액이고, 환불 회수로 음수일 수 있다", example = "50000")
    val paidBalance: Long,
)
