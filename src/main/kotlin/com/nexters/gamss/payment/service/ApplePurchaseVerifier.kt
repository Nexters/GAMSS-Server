package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.apple.AppleTransaction
import com.nexters.gamss.payment.apple.AppleTransactionDecoder
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.domain.VerifiedPurchase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * App Store 가 서명한 거래를 풀어, 지급([PurchaseGrantService])에 넘길 [VerifiedPurchase] 를 만든다.
 *
 * [GooglePurchaseVerifier] 와 같은 원칙으로 거래 내용만 판정한다. 계정 토큰의 주인과 카탈로그는 지급이
 * 판정한다. 요청 회원을 받지 않으므로 서버 알림(#245)도 이 검증기를 그대로 쓴다.
 *
 * 앱은 [ErrorCode.PURCHASE_INVALID] 와 [ErrorCode.PURCHASE_CANCELLED] 를 받으면 거래를 finish 한다(#243).
 * Apple 은 finish 하지 않은 거래를 자동 환불하지 않고 다시 보내 주므로, 지급할 수 있었던 거래를 이 두
 * 코드로 거부하면 돈만 나가고 복구되지 않는다. 확실한 영구 실패에만 쓴다.
 */
@Component
class ApplePurchaseVerifier(
    private val decoder: AppleTransactionDecoder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun verify(signedTransaction: String): VerifiedPurchase {
        val transaction = decoder.decode(signedTransaction)
        val transactionId = requireValue(transaction.transactionId, "transactionId", transaction)
        val productId = requireValue(transaction.productId, "productId", transaction)
        if (transaction.type != CONSUMABLE) {
            log.warn("소모성이 아닌 App Store 거래라 지급하지 않았습니다: transactionId={}, type={}", transactionId, transaction.type)
            throw BusinessException(ErrorCode.PURCHASE_INVALID)
        }
        if (transaction.revocationDate != null) {
            throw BusinessException(ErrorCode.PURCHASE_CANCELLED)
        }
        return VerifiedPurchase(
            platform = PaymentPlatform.APPLE,
            storeTransactionKey = StoreTransactionKey(transactionId),
            accountToken = transaction.appAccountToken,
            productId = productId,
            quantity = quantityOf(transaction, transactionId),
            environment = transaction.environment,
            purchasedAt = purchasedAtOf(transaction, transactionId),
        )
    }

    private fun requireValue(
        value: String?,
        name: String,
        transaction: AppleTransaction,
    ): String {
        if (!value.isNullOrBlank()) {
            return value
        }
        log.warn("App Store 거래에 {} 가 없어 지급하지 않았습니다: transactionId={}", name, transaction.transactionId)
        throw BusinessException(ErrorCode.PURCHASE_INVALID)
    }

    /**
     * Apple 은 앱이 넘긴 수량만큼 한 거래로 결제하고 사용자는 실제로 그만큼 냈으므로, Google(#244)과 달리
     * 여러 개도 지급한다. 없으면 1 이다. Apple 상한(10)을 벗어난 값은 우리가 아는 거래가 아니다.
     */
    private fun quantityOf(
        transaction: AppleTransaction,
        transactionId: String,
    ): Int {
        val quantity = transaction.quantity ?: return 1
        if (quantity in 1..MAX_QUANTITY) {
            return quantity
        }
        log.error(
            "App Store 거래 수량이 범위를 벗어나 지급하지 않았습니다. 수동 처리가 필요합니다: transactionId={}, productId={}, quantity={}",
            transactionId,
            transaction.productId,
            quantity,
        )
        throw BusinessException(ErrorCode.PURCHASE_INVALID)
    }

    /** 서명된 거래에 구매 시각이 없을 수는 없다. 돈이 나갔을 수 있어 finish 시키지 않도록 503 으로 둔다. */
    private fun purchasedAtOf(
        transaction: AppleTransaction,
        transactionId: String,
    ) = transaction.purchaseDate ?: run {
        log.error("App Store 거래에 구매 시각이 없습니다: transactionId={}", transactionId)
        throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
    }

    private companion object {
        const val CONSUMABLE = "Consumable"
        const val MAX_QUANTITY = 10
    }
}
