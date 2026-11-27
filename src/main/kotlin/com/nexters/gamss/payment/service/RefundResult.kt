package com.nexters.gamss.payment.service

/**
 * [changedTokens] 는 이번 호출로 바뀐 회수량이다. 양수는 더 회수, 음수는 환불 취소다. 0 은 이미 맞춰진 상태이거나, 이미
 * 반영한 출처가 다시 왔거나(목표와 관계없이), 환불 취소를 허용하지 않는 호출에 지금 회수량보다 작은 목표가 온 경우다.
 */
data class RefundResult(
    val paymentId: Long,
    val refundedTokens: Long,
    val changedTokens: Long,
)

/** 회수 목표(지금까지 회수됐어야 할 총량)와 그 출처. */
data class RefundGoal(
    val targetRefundedTokens: Long,
    val sourceId: String,
)
