package com.nexters.gamss.tokenlimit.service

/** 회원의 오늘 사용 토큰과 일일 상한. [dailyLimit]이 null이면 상한 미적용(무제한) 환경이다. */
data class TokenUsage(
    val usedTokens: Long,
    val dailyLimit: Long?,
    val exceeded: Boolean,
)
