package com.nexters.gamss.payment.apple

import com.nexters.gamss.payment.domain.PaymentEnvironment
import java.time.Instant

/**
 * App Store 가 서명한 거래 JWS(`Transaction.jwsRepresentation`, 서버 알림의 `signedTransactionInfo`)를
 * 검증하고 푸는 경계. 라이브러리 타입은 이 경계 밖으로 내보내지 않는다.
 *
 * 서명, 인증서 체인, 환경이 맞지 않으면 [com.nexters.gamss.global.exception.ErrorCode.PURCHASE_INVALID],
 * 다시 시도하면 풀릴 수 있는 검증 실패와 bundleId 불일치(설정 오류일 수 있음)는
 * [com.nexters.gamss.global.exception.ErrorCode.PAYMENT_STORE_UNAVAILABLE] 로 던진다.
 */
interface AppleTransactionDecoder {
    fun decode(signedTransaction: String): AppleTransaction
}

/** 서명이 확인된 거래에서 쓰는 값만 옮긴 것. 해석은 검증기가 한다. */
data class AppleTransaction(
    val transactionId: String?,
    val productId: String?,
    /** `Consumable`, `Non-Consumable` 등 원문. */
    val type: String?,
    val quantity: Int?,
    val appAccountToken: String?,
    val purchaseDate: Instant?,
    /** 환불되거나 취소된 시각. 없으면 유효한 거래다. */
    val revocationDate: Instant?,
    val environment: PaymentEnvironment,
)
