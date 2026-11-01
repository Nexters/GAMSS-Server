package com.nexters.gamss.wallet.domain

class TokenWalletChange(
    val subjectKey: String,
    val type: TokenLedgerType,
    val amount: TokenAmount,
    val refId: LedgerRefId?,
) {
    init {
        require(type.requiresRefId == (refId != null)) { "$type 의 멱등 키 여부가 맞지 않습니다: refId=$refId" }
    }
}
