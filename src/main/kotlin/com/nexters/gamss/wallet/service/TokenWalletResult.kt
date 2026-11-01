package com.nexters.gamss.wallet.service

/** [balance] 는 반영 직후의 현재 잔액이다. 재요청이면 원래 반영량과 지금 잔액을 돌려준다. */
data class TokenWalletResult(
    val appliedAmount: Long,
    val balance: Long,
    val replayed: Boolean,
)
