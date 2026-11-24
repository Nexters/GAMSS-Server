package com.nexters.gamss.payment.service

import com.nexters.gamss.wallet.service.TokenWalletReader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 앱이 보낸 App Store 거래를 검증하고 지급한다. Google 과 달리 consume 단계가 없다. 거래를 끝내는
 * finish 는 앱이 응답을 보고 한다.
 *
 * 지급한 뒤 이 거래의 환불 알림이 먼저 와 있었으면 바로 회수한다([AppleRefundSynchronizer]).
 *
 * 지급([PurchaseGrantService])은 트랜잭션 밖에서 불러야 하므로 이 서비스에도 트랜잭션을 걸지 않는다.
 */
@Service
class ApplePurchaseService(
    private val verifier: ApplePurchaseVerifier,
    private val grantService: PurchaseGrantService,
    private val refundSynchronizer: AppleRefundSynchronizer,
    private val walletReader: TokenWalletReader,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun purchase(
        memberId: Long,
        signedTransaction: String,
    ): ApplePurchaseResult {
        val purchase = verifier.verify(signedTransaction)
        val grant = grantService.grant(memberId, purchase)
        val refund = refundSynchronizer.applyRecorded(purchase.storeTransactionKey)
        val changed = refund?.changedTokens ?: 0
        if (changed != 0L) {
            log.warn(
                "환불 알림이 먼저 와 있던 거래라 지급 직후 회수했습니다: paymentId={}, memberId={}, changedTokens={}",
                grant.paymentId,
                memberId,
                changed,
            )
        }
        return ApplePurchaseResult(
            productId = purchase.productId,
            grantedTokens = grant.grantedTokens,
            // 다른 요청(환불 알림)이 지급 직후 먼저 회수했을 수 있어 지급 결과가 아니라 지금 잔액을 읽는다.
            paidBalance = walletReader.balanceOf(memberId),
        )
    }
}
