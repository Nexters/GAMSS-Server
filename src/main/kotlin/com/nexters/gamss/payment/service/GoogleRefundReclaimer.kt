package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.google.GooglePlayClient
import com.nexters.gamss.payment.repository.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Google 구매의 환불을 회수한다. RTDN 웹훅과 Voided Purchases 대사 배치가 함께 쓴다.
 *
 * [PurchaseGrantService.applyRefund] 는 "지금까지 회수됐어야 할 총량"을 목표로 받는다. 그래서 대사 항목에 실린 이번
 * 환불 한 건의 수량(`voidedQuantity`)을 목표로 쓰지 않는다. RTDN 알림에는 수량이 없고 환불 종류(`refundType`)만 있다.
 * 부분 환불이면 구매를 다시 조회해 `quantity - refundableQuantity`(지금까지 환불된 총 수량)로 목표를 계산한다. 웹훅과
 * 배치가 같은 환불을 가져와도 목표가 같으므로 두 번째는 차이 0 으로 끝난다.
 *
 * 전액 환불은 Google 을 조회하지 않고 지급량 전체를 목표로 삼는다. 근거는 부르는 쪽에 있다. 대사 배치는 Google 이
 * 돌려준 환불 목록이고, 웹훅은 push 인증과 RTDN 토픽의 게시 권한이다([GoogleNotificationService]).
 *
 * 수량 1 결제는 수량으로 나눠 환불할 수 없으므로 부분 환불로 와도 전액 환불로 보고, Google 을 조회하지 않는다. Google 의 수량
 * 기반 부분 환불은 다중 수량 구매에만 있지만, 대사 항목의 `voidedQuantity` 나 알림의 환불 종류가 수량 1 구매에 붙어
 * 왔을 때 그대로 계산하면 `refundableQuantity` 에 따라 목표가 0 이 되거나(값이 1 일 때) 회수를 거부한다(값이 없을 때).
 *
 * Google 일회성 상품의 환불은 되돌리는 알림이 없으므로 회수량을 올리기만 한다(`allowReversal = false`). 부분 환불 목표는
 * 결제 행을 잠그기 전에 Google 을 조회해 계산한다. 그 사이 다른 출처가 더 많이 회수해 커밋하면 이 목표는 이미 회수한 양보다
 * 작아지고, 이를 환불 취소로 처리하면 회수한 토큰을 돌려주게 된다.
 *
 * **트랜잭션 밖에서 부른다.** Google 조회가 끼고, 회수 자체도 트랜잭션 밖에서 불러야 한다.
 */
@Component
class GoogleRefundReclaimer(
    private val client: GooglePlayClient,
    private val paymentRepository: PaymentRepository,
    private val grantService: PurchaseGrantService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * [partial] 이면 수량 기반 부분 환불이다. 다만 수량 1 결제는 전액 환불로 본다. [sourceId] 는 원장 환불 출처이고
     * 49자 이하여야 한다.
     *
     * 결제 기록이 없으면 `PAYMENT_NOT_FOUND` 를 던진다. 지급 전에 환불됐거나, 다른 환경(dev 구독이 받은 prod 구매)의
     * 구매다. 판단은 부르는 쪽이 한다.
     */
    fun reclaim(
        purchaseToken: String,
        partial: Boolean,
        sourceId: String,
    ): RefundResult {
        val key = StoreTransactionKey.ofGooglePurchaseToken(purchaseToken)
        val payment =
            paymentRepository.findByPlatformAndStoreTransactionKey(PaymentPlatform.GOOGLE, key.value)
                ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)
        val target =
            if (partial && payment.quantity > 1) {
                partialTarget(purchaseToken, payment.grantedTokens, key)
            } else {
                payment.grantedTokens
            }
        val result = grantService.applyRefund(PaymentPlatform.GOOGLE, key, target, sourceId, allowReversal = false)
        if (result.refundedTokens > target) {
            log.warn(
                "Google 환불 목표가 이미 회수한 양보다 작아 회수량을 줄이지 않았습니다. Google 을 조회한 뒤 결제를 잠그기 전에 " +
                    "다른 출처의 회수가 먼저 커밋됐을 수 있습니다: paymentId={}, storeOrderId={}, target={}, refundedTokens={}, sourceId={}",
                result.paymentId,
                payment.storeOrderId,
                target,
                result.refundedTokens,
                sourceId,
            )
        }
        if (result.changedTokens != 0L) {
            log.info(
                "Google 환불을 회수했습니다: paymentId={}, storeOrderId={}, changedTokens={}, refundedTokens={}, sourceId={}",
                result.paymentId,
                payment.storeOrderId,
                result.changedTokens,
                result.refundedTokens,
                sourceId,
            )
        }
        return result
    }

    /**
     * `granted_tokens × (quantity - refundableQuantity) / quantity`. 나누어떨어지지 않으면 내림한다 - 덜 회수하는 쪽이
     * 사용자에게 불리하지 않다. 값을 읽을 수 없으면 회수량을 정할 수 없으므로 거부하고 ERROR 를 남긴다.
     */
    private fun partialTarget(
        purchaseToken: String,
        grantedTokens: Long,
        key: StoreTransactionKey,
    ): Long {
        val item = client.getPurchase(purchaseToken).lineItems.singleOrNull()
        val quantity = item?.quantity ?: 1
        val refundable = item?.refundableQuantity
        if (refundable == null || quantity <= 0 || refundable !in 0..quantity) {
            log.error(
                "부분 환불인데 환불 가능 수량을 읽을 수 없어 회수하지 않았습니다. 수동 처리가 필요합니다: key={}, quantity={}, refundableQuantity={}",
                key,
                quantity,
                refundable,
            )
            throw BusinessException(ErrorCode.PURCHASE_INVALID)
        }
        return refundTarget(grantedTokens, quantity, refundable)
    }

    companion object {
        fun refundTarget(
            grantedTokens: Long,
            quantity: Int,
            refundableQuantity: Int,
        ): Long = grantedTokens * (quantity - refundableQuantity) / quantity
    }
}
