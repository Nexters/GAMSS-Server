package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.apple.AppleNotification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * `ONE_TIME_CHARGE`: 소모성 구매마다 온다. 앱이 지급 요청을 보내지 못한 구매(결제 직후 앱 종료·삭제)를 지급한다.
 *
 * 이미 앱 요청으로 지급된 거래면 지급은 재요청으로 끝난다. 계정 토큰이 없거나 모르는 값이면 지급 쪽이 로그를 남기고
 * 거부한다. 지급한 뒤에는 먼저 와 있던 환불 상태가 있으면 바로 회수한다.
 */
@Component
class AppleOneTimeChargeHandler(
    private val verifier: ApplePurchaseVerifier,
    private val grantService: PurchaseGrantService,
    private val refundSynchronizer: AppleRefundSynchronizer,
) : AppleNotificationHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override val types = setOf("ONE_TIME_CHARGE")

    override fun handle(notification: AppleNotification) {
        val signedTransaction =
            notification.signedTransactionInfo ?: run {
                log.error("구매 알림에 거래 정보가 없습니다: notificationUuid={}", notification.notificationUuid)
                return
            }
        val purchase =
            try {
                verifier.verify(signedTransaction)
            } catch (e: BusinessException) {
                if (e.errorCode == ErrorCode.PURCHASE_CANCELLED) {
                    // 지급 전에 환불됐다. 환불 알림이 따로 온다.
                    log.info("구매 알림이 왔지만 이미 환불된 거래입니다: notificationUuid={}", notification.notificationUuid)
                    return
                }
                throw e
            }
        val grant = grantService.grantFromStoreNotification(purchase)
        if (!grant.replayed) {
            log.info("앱 요청 없이 App Store 알림으로 지급했습니다: paymentId={}, transactionId={}", grant.paymentId, purchase.storeTransactionKey)
        }
        refundSynchronizer.applyRecorded(purchase.storeTransactionKey)
    }
}
