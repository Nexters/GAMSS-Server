package com.nexters.gamss.payment.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 앱이 보낸 Google 구매를 검증하고, 지급하고, consume 한다.
 *
 * 세 단계 모두 트랜잭션 밖에서 차례로 부른다. 검증은 외부 호출이고, 지급은 자기 트랜잭션을
 * 커밋한 뒤 돌아오며([PurchaseGrantService]), consume 은 그 커밋 뒤에 해야 한다. 지급이 롤백됐는데
 * consume 이 끝나 있으면 사용자는 토큰을 받지 못한 채 구매가 확정된다.
 *
 * 같은 구매를 다시 보내면 지급은 처음 결과를 돌려주고, consume 이 안 끝났으면 이번에 다시 시도한다.
 */
@Service
class GooglePurchaseService(
    private val verifier: GooglePurchaseVerifier,
    private val grantService: PurchaseGrantService,
    private val consumer: GooglePurchaseConsumer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun purchase(
        memberId: Long,
        productId: String,
        purchaseToken: String,
    ): GooglePurchaseResult =
        when (val verification = verifier.verify(purchaseToken, productId)) {
            is GooglePurchaseVerification.Pending -> GooglePurchaseResult.Pending(verification.productId)
            is GooglePurchaseVerification.Verified -> grantAndConsume(memberId, verification)
        }

    private fun grantAndConsume(
        memberId: Long,
        verification: GooglePurchaseVerification.Verified,
    ): GooglePurchaseResult.Granted {
        val purchase = verification.purchase
        val grant = grantService.grant(memberId, purchase)
        consumeQuietly(grant.paymentId, purchase.productId, requireNotNull(purchase.storePurchaseToken), verification.consumed)
        return GooglePurchaseResult.Granted(
            productId = purchase.productId,
            grantedTokens = grant.grantedTokens,
            paidBalance = grant.balance,
        )
    }

    /**
     * 토큰은 이미 지급됐으므로 consume 이 어떻게 끝나든 사용자에게는 성공으로 응답한다. 실패한 consume 은
     * 재시도 스케줄러가 다시 시도한다. 예상하지 못한 예외도 여기서 막는다 - 실패 응답을 받은 앱은
     * 같은 구매를 다시 보내고, 그동안 사용자는 받은 토큰을 못 받았다고 생각한다.
     */
    private fun consumeQuietly(
        paymentId: Long,
        productId: String,
        purchaseToken: String,
        alreadyConsumed: Boolean,
    ) {
        try {
            consumer.consume(paymentId, productId, purchaseToken, alreadyConsumed)
        } catch (e: RuntimeException) {
            log.error("consume 처리 중 예상하지 못한 오류가 났습니다. 재시도 스케줄러가 다시 시도합니다: paymentId={}", paymentId, e)
        }
    }
}

sealed interface GooglePurchaseResult {
    val productId: String

    /** [paidBalance] 는 응답 시점의 유료 잔액이다. 재요청이면 처음 응답과 다를 수 있다. */
    class Granted(
        override val productId: String,
        val grantedTokens: Long,
        val paidBalance: Long,
    ) : GooglePurchaseResult

    class Pending(
        override val productId: String,
    ) : GooglePurchaseResult
}
