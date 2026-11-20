package com.nexters.gamss.payment.service

import com.nexters.gamss.payment.domain.AppleRefundState
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.repository.AppleNotificationRecordRepository
import com.nexters.gamss.payment.repository.PaymentRepository
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * App Store 거래의 환불 상태를 결제 기록에 맞춘다. 환불 알림과 지급(앱 요청, 구매 알림)이 함께 쓴다.
 *
 * 환불 알림이 지급보다 먼저 올 수 있다. 구매 직후의 JWS 를 들고 있다가 환불받은 뒤 보내면 JWS 의 revocationDate 는 서명
 * 시점 값이라 비어 있어 지급이 그대로 진행된다(#254 리뷰). 그래서 결제 기록이 없어도 상태는 남기고, 지급한 쪽이 지급을
 * 커밋한 뒤 [applyRecorded] 로 바로 회수한다. 지급하지 않고 거부하면 결제 기록이 없어 나중에 환불 취소가 와도 돌려줄 수 없다.
 *
 * 두 쪽이 동시에 와도 놓치지 않는다. 환불 쪽은 상태를 커밋한 뒤 결제를 보고, 지급 쪽은 결제를 커밋한 뒤 상태를 본다.
 * 그래서 적어도 한쪽은 둘 다 본다. 둘 다 회수해도 같은 출처, 같은 목표라 한 번만 깎인다.
 *
 * [PurchaseGrantService] 를 부르므로 트랜잭션 밖에서 불러야 한다.
 */
@Component
class AppleRefundSynchronizer(
    private val recordRepository: AppleNotificationRecordRepository,
    private val paymentRepository: PaymentRepository,
    private val grantService: PurchaseGrantService,
) {
    /** 상태를 남기고 결제 기록이 있으면 맞춘다. 결제 기록이 아직 없으면 null 이다. */
    fun record(
        transactionId: StoreTransactionKey,
        state: AppleRefundState,
        signedAt: Instant,
        sourceId: String,
    ): RefundResult? {
        recordRepository.saveRefundStateIfNewer(transactionId, state, signedAt, sourceId, Instant.now())
        return applyRecorded(transactionId)
    }

    /**
     * 저장된 최신 상태로 회수량을 맞춘다. 상태나 결제 기록이 없으면 null 이다.
     *
     * 알림 본문이 아니라 저장된 상태를 쓰므로, 늦게 도착한 옛 알림도 최신 상태와 그 출처로 맞춰 아무것도 바꾸지 않는다.
     */
    fun applyRecorded(transactionId: StoreTransactionKey): RefundResult? {
        val recorded = recordRepository.findRefundState(transactionId) ?: return null
        val payment = paymentRepository.findByPlatformAndStoreTransactionKey(PaymentPlatform.APPLE, transactionId.value) ?: return null
        return grantService.applyRefund(
            platform = PaymentPlatform.APPLE,
            storeTransactionKey = transactionId,
            targetRefundedTokens = recorded.state.targetFor(payment.grantedTokens),
            sourceId = recorded.sourceId,
            allowReversal = true,
        )
    }
}
