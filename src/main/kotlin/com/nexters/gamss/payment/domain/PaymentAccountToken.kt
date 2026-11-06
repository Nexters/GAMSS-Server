package com.nexters.gamss.payment.domain

import java.util.UUID

/**
 * 스토어에 넘기는 구매 계정 토큰(Apple `appAccountToken`, Google `obfuscatedAccountId`).
 *
 * 값은 무작위 UUID 다. Apple 은 UUID 타입을 요구하고, Google 은 개인 식별 정보를 넣지 말라고 한다.
 * 이 클래스는 형식만 보장하고 만드는 일은 하지 않는다 - 난수는 도메인 밖에서 만든다
 * ([com.nexters.gamss.payment.service.PaymentAccountService]).
 */
class PaymentAccountToken private constructor(
    val value: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is PaymentAccountToken) {
            return false
        }
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        const val LENGTH = 36

        fun of(uuid: UUID): PaymentAccountToken = PaymentAccountToken(uuid.toString())

        /**
         * 스토어가 돌려준 값을 소문자 표준 표기로 맞춘다. 서버가 만든 값은 소문자인데, 스토어가
         * 대문자로 돌려주더라도 같은 계정으로 봐야 한다. 형식에 맞지 않으면 null 이다.
         */
        fun parseOrNull(raw: String?): PaymentAccountToken? {
            if (raw == null || raw.length != LENGTH) {
                return null
            }
            return runCatching { of(UUID.fromString(raw)) }.getOrNull()
        }
    }
}
