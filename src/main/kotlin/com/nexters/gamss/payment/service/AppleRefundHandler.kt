package com.nexters.gamss.payment.service

import com.nexters.gamss.payment.apple.AppleNotification
import com.nexters.gamss.payment.apple.AppleTransactionDecoder
import com.nexters.gamss.payment.domain.AppleRefundState
import com.nexters.gamss.payment.domain.StoreTransactionKey
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * `REFUND`, `REFUND_REVERSED`: 거래의 환불 상태를 남기고 결제 기록에 맞춘다([AppleRefundSynchronizer]).
 *
 * 알림 종류로 환불 여부를, 거래 정보로 비율과 서명 시각을 정한다. 서명 시각이 저장된 상태보다 오래된 알림은 상태를
 * 바꾸지 않는다. 그래서 환불 취소 뒤에 옛 환불 알림이 재전송돼 와도 다시 회수하지 않는다.
 *
 * 거래 서명은 [ApplePurchaseVerifier] 가 아니라 디코더로 직접 푼다. 검증기는 환불된 거래를 거부한다. 알림 서명은
 * 통과했는데 거래 서명만 실패하면 우리 설정 문제라 디코더가 `PAYMENT_STORE_UNAVAILABLE` 로 던지고, 알림을 다시 받는다.
 */
@Component
class AppleRefundHandler(
    private val decoder: AppleTransactionDecoder,
    private val refundSynchronizer: AppleRefundSynchronizer,
) : AppleNotificationHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override val types = setOf(REFUND, REFUND_REVERSED)

    override fun handle(notification: AppleNotification) {
        val uuid = notification.notificationUuid
        val signedTransaction =
            notification.signedTransactionInfo ?: run {
                log.error("환불 알림에 거래 정보가 없습니다: notificationUuid={}, type={}", uuid, notification.type)
                return
            }
        val transaction = decoder.decode(signedTransaction)
        val transactionId =
            transaction.transactionId ?: run {
                log.error("환불 알림의 거래에 transactionId 가 없습니다: notificationUuid={}", uuid)
                return
            }
        if (transaction.type != CONSUMABLE) {
            log.info("소모성이 아닌 거래의 환불 알림이라 무시합니다: notificationUuid={}, type={}", uuid, transaction.type)
            return
        }
        val state =
            if (notification.type == REFUND) {
                AppleRefundState.refunded(transaction.revocationPercentage)
            } else {
                AppleRefundState.NOT_REFUNDED
            }
        val signedAt =
            transaction.signedDate ?: notification.signedDate ?: run {
                log.error("환불 알림에 서명 시각이 없어 순서를 정할 수 없습니다: notificationUuid={}", uuid)
                return
            }
        val result = refundSynchronizer.record(StoreTransactionKey(transactionId), state, signedAt, "$SOURCE_PREFIX$uuid")
        if (result == null) {
            log.warn(
                "결제 기록이 없는 거래의 환불 상태를 남겼습니다. 지급되면 바로 반영합니다: notificationUuid={}, transactionId={}, type={}",
                uuid,
                transactionId,
                notification.type,
            )
            return
        }
        log.info(
            "App Store 환불 상태를 반영했습니다: notificationUuid={}, paymentId={}, type={}, revocationPercentage={}, changedTokens={}",
            uuid,
            result.paymentId,
            notification.type,
            transaction.revocationPercentage,
            result.changedTokens,
        )
    }

    private companion object {
        const val REFUND = "REFUND"
        const val REFUND_REVERSED = "REFUND_REVERSED"
        const val CONSUMABLE = "Consumable"

        /** 원장 환불 출처. UUID 36자와 합쳐 49자 안에 들어간다. */
        const val SOURCE_PREFIX = "asn:"
    }
}
