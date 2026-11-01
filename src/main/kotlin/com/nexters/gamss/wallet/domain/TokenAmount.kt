package com.nexters.gamss.wallet.domain

class TokenAmount(
    val value: Long,
) {
    init {
        require(value > 0) { "토큰 양은 양수여야 합니다: $value" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is TokenAmount) {
            return false
        }
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value.toString()
}
