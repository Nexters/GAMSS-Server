package com.nexters.gamss.wallet.domain

/**
 * 잔액이 바뀐 이유. 증감 방향과 잔액 하한 규칙을 타입이 정하므로 호출부는 양만 넘긴다.
 *
 * 사용([CONSUME])은 0에서 멈추고, 회수는 음수를 허용한다(#237). 사용을 뺀 나머지는 같은 원인이
 * 두 번 반영되지 않도록 멱등 키가 반드시 있어야 한다.
 */
enum class TokenLedgerType(
    val credit: Boolean,
    val stopsAtZero: Boolean,
    val requiresRefId: Boolean,
) {
    PURCHASE(credit = true, stopsAtZero = false, requiresRefId = true),
    CONSUME(credit = false, stopsAtZero = true, requiresRefId = false),
    REFUND(credit = false, stopsAtZero = false, requiresRefId = true),
    REFUND_REVERSED(credit = true, stopsAtZero = false, requiresRefId = true),
    ADMIN_GRANT(credit = true, stopsAtZero = false, requiresRefId = true),
    ADMIN_REVOKE(credit = false, stopsAtZero = false, requiresRefId = true),
    ;

    fun signed(amount: TokenAmount): Long {
        if (credit) {
            return amount.value
        }
        return -amount.value
    }
}
