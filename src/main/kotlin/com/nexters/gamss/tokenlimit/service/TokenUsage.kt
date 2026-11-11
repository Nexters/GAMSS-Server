package com.nexters.gamss.tokenlimit.service

/**
 * 회원의 오늘 사용 토큰, 일일 상한, 유료 잔액. [dailyLimit]이 null이면 상한 미적용(무제한) 환경이다.
 * [usedTokens]에는 일일 토큰을 다 쓴 뒤 유료 토큰으로 쓴 몫도 들어가므로 [dailyLimit]보다 클 수 있다.
 * [paidBalance]는 리셋과 상관없는 누적 잔액이고, 환불 회수로 음수일 수 있다.
 */
data class TokenUsage(
    val usedTokens: Long,
    val dailyLimit: Long?,
    val exceeded: Boolean,
    val paidBalance: Long,
)
