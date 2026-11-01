package com.nexters.gamss.wallet.domain

class PaidTokenBalance(
    val subjectKey: String,
    val value: Long,
) {
    /** 반영할 양이 없으면 null 이다. 잔액이 0 이하일 때의 사용이 그렇다. */
    fun apply(change: TokenWalletChange): TokenLedgerEntry? {
        require(change.subjectKey == subjectKey) { "다른 주체의 지갑에 반영할 수 없습니다." }
        val delta = deltaOf(change)
        if (delta == 0L) {
            return null
        }
        return TokenLedgerEntry(subjectKey, change.type, delta, Math.addExact(value, delta), change.refId?.value)
    }

    private fun deltaOf(change: TokenWalletChange): Long {
        val delta = change.type.signed(change.amount)
        if (!change.type.stopsAtZero) {
            return delta
        }
        return maxOf(delta, -maxOf(value, 0))
    }
}
