package com.nexters.gamss.payment.service

import org.springframework.stereotype.Service

/**
 * 앱이 보낸 App Store 거래를 검증하고 지급한다. Google 과 달리 consume 단계가 없다. 거래를 끝내는
 * finish 는 앱이 응답을 보고 한다.
 *
 * 지급([PurchaseGrantService])은 트랜잭션 밖에서 불러야 하므로 이 서비스에도 트랜잭션을 걸지 않는다.
 */
@Service
class ApplePurchaseService(
    private val verifier: ApplePurchaseVerifier,
    private val grantService: PurchaseGrantService,
) {
    fun purchase(
        memberId: Long,
        signedTransaction: String,
    ): ApplePurchaseResult {
        val purchase = verifier.verify(signedTransaction)
        val grant = grantService.grant(memberId, purchase)
        return ApplePurchaseResult(
            productId = purchase.productId,
            grantedTokens = grant.grantedTokens,
            paidBalance = grant.balance,
        )
    }
}
