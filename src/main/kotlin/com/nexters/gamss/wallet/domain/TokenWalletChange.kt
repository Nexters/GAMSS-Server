package com.nexters.gamss.wallet.domain

class TokenWalletChange(
    val subjectKey: String,
    val type: TokenLedgerType,
    val amount: TokenAmount,
    val refId: LedgerRefId?,
) {
    init {
        require(SUBJECT_KEY.matches(subjectKey)) { "주체 키 형식이 아닙니다: length=${subjectKey.length}" }
        require(type.requiresRefId == (refId != null)) { "$type 의 멱등 키 여부가 맞지 않습니다: refId=$refId" }
    }

    private companion object {
        /** [com.nexters.gamss.member.service.SubjectKeyGenerator] 가 만드는 소문자 hex 64자. */
        val SUBJECT_KEY = Regex("[0-9a-f]{64}")
    }
}
