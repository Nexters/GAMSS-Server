package com.nexters.gamss.payment.service

/** [paidBalance] 는 응답 시점의 유료 잔액이다. 재요청이면 처음 응답과 다를 수 있다. */
data class ApplePurchaseResult(
    val productId: String,
    val grantedTokens: Long,
    val paidBalance: Long,
)
