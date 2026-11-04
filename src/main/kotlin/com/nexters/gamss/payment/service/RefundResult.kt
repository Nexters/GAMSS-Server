package com.nexters.gamss.payment.service

/** [changedTokens] 는 이번 호출로 바뀐 회수량이다. 양수는 더 회수, 음수는 환불 취소, 0 은 이미 맞춰진 상태다. */
data class RefundResult(
    val paymentId: Long,
    val refundedTokens: Long,
    val changedTokens: Long,
)
