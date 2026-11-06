package com.nexters.gamss.payment.service

/** [balance] 는 반영 직후의 유료 토큰 잔액이다. 이미 지급한 거래면 [replayed] 가 true 이고 처음 지급량을 돌려준다. */
data class GrantResult(
    val paymentId: Long,
    val grantedTokens: Long,
    val balance: Long,
    val replayed: Boolean,
)
