package com.nexters.gamss.wallet.domain

/** 원장 멱등 키. 형식(`purchase:...`, `refund:...`)은 부르는 쪽이 정한다. */
class LedgerRefId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "멱등 키는 비어 있을 수 없습니다." }
        require(value.length <= MAX_LENGTH) { "멱등 키는 ${MAX_LENGTH}자 이하여야 합니다: ${value.length}" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is LedgerRefId) {
            return false
        }
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 128
    }
}
