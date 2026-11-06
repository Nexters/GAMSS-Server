package com.nexters.gamss.payment.domain

/**
 * 스토어 거래를 하나로 가리키는 키. Apple 은 `transactionId`, Google 은 `SHA-256(purchaseToken)` hex 다.
 *
 * 64자로 묶는 이유는 원장 멱등 키(`refund:{platform}:{key}:{sourceId}`)가 128자 안에 들어가야 해서다
 * ([com.nexters.gamss.wallet.domain.LedgerRefId.MAX_LENGTH]). Google 키가 정확히 64자이고 Apple 의
 * 숫자 거래 ID 는 그보다 훨씬 짧다.
 */
class StoreTransactionKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "거래 키는 비어 있을 수 없습니다." }
        require(value.length <= MAX_LENGTH) { "거래 키는 ${MAX_LENGTH}자 이하여야 합니다: ${value.length}" }
        require(value.none { it.isWhitespace() || it == ':' }) { "거래 키에 공백이나 구분자(:)를 쓸 수 없습니다." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is StoreTransactionKey) {
            return false
        }
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 64
    }
}
