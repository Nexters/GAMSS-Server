package com.nexters.gamss.payment.domain

import java.security.MessageDigest

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

        /**
         * Google 구매의 거래 키. `purchaseToken` 은 길이 제한이 문서화돼 있지 않아 그대로 키로 쓸 수 없고,
         * SHA-256 hex 는 정확히 64자다. 앱 요청, 웹훅(#246), 대사 배치가 모두 이 함수로 키를 만들어야
         * 같은 구매가 같은 거래로 묶인다. 소문자 hex 로 고정한다 - 대소문자가 어긋나면 다른 거래가 된다.
         */
        fun ofGooglePurchaseToken(purchaseToken: String): StoreTransactionKey {
            require(purchaseToken.isNotBlank()) { "purchaseToken 은 비어 있을 수 없습니다." }
            val digest = MessageDigest.getInstance("SHA-256").digest(purchaseToken.toByteArray(Charsets.UTF_8))
            return StoreTransactionKey(digest.joinToString("") { "%02x".format(it) })
        }
    }
}
