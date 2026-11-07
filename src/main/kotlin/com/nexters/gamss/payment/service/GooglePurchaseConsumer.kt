package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.google.GooglePlayClient
import com.nexters.gamss.payment.repository.PaymentRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant

/**
 * 지급한 Google 구매를 consume 하고 `payment.consumed_at` 을 채운다. 앱 요청 경로, 재시도 스케줄러,
 * RTDN 웹훅(#246)이 함께 쓴다.
 *
 * 일회성 구매는 3일 안에 acknowledge 하지 않으면 Google 이 환불하고 구매를 취소한다. consume 도
 * acknowledge 로 친다. 그런데 그때는 토큰이 이미 지급돼 있으므로 consume 실패가 곧 손실이다. 그래서
 * 실패해도 여기서 끝내지 않고 `consumed_at = NULL` 로 남겨 스케줄러가 다시 시도하게 한다.
 *
 * **트랜잭션 밖에서 부른다.** 외부 호출을 DB 트랜잭션 안에 두면 Google 이 느릴 때 커넥션을 붙잡고,
 * 지급이 롤백돼도 consume 은 되돌릴 수 없다.
 */
@Component
class GooglePurchaseConsumer(
    private val client: GooglePlayClient,
    private val paymentRepository: PaymentRepository,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** consume 호출 실패 건수(#247 의 `gamss.payment.google.consume.failure`). 처음부터 0 으로 등록한다. */
    private val failureCounter =
        Counter
            .builder(FAILURE_METRIC)
            .description("Google Play consume 호출이 실패한 횟수. 실패한 결제는 재시도 스케줄러가 다시 시도한다")
            .register(meterRegistry)

    /**
     * [alreadyConsumed] 면 Google 을 부르지 않고 기록만 한다. 이미 consume 된 구매에 consume 을 다시
     * 부를 때의 응답은 문서화돼 있지 않다.
     *
     * Google 이 consume 을 거부했거나 Google 과 통신할 수 없거나 consume 뒤 기록하지 못하면, 예외 대신
     * [GoogleConsumeOutcome] 으로 돌려준다. 그 밖의 예외(트랜잭션 안에서 부른 경우, 기록 트랜잭션을 시작하지 못한
     * 경우 등)는 그대로 올라오므로, 지급 뒤에 실패로 응답하면 안 되는 쪽은 [GooglePurchaseService] 처럼 직접 막는다.
     * 앱 요청 경로는 결과와 관계없이 성공으로 응답하고, 스케줄러는 Google 과 통신할 수 없을 때 그 회차를 멈춘다.
     */
    fun consume(
        paymentId: Long,
        productId: String,
        purchaseToken: String,
        alreadyConsumed: Boolean,
    ): GoogleConsumeOutcome {
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "consume 은 트랜잭션 밖에서 불러야 합니다 - 외부 호출이 커넥션을 붙잡습니다."
        }
        if (!alreadyConsumed) {
            try {
                client.consume(productId, purchaseToken)
            } catch (e: BusinessException) {
                failureCounter.increment()
                log.warn("Google 구매를 consume 하지 못했습니다. 재시도 스케줄러가 다시 시도합니다: paymentId={}, error={}", paymentId, e.errorCode)
                return if (e.errorCode == ErrorCode.PAYMENT_STORE_UNAVAILABLE) {
                    GoogleConsumeOutcome.STORE_UNAVAILABLE
                } else {
                    GoogleConsumeOutcome.FAILED
                }
            }
        }
        try {
            paymentRepository.markConsumed(paymentId, Instant.now())
        } catch (e: DataAccessException) {
            // Google 쪽은 consume 됐으므로 다음 재시도가 상태만 보고 기록을 채운다.
            log.warn("consume 은 끝났지만 기록하지 못했습니다. 재시도 스케줄러가 채웁니다: paymentId={}", paymentId, e)
            return GoogleConsumeOutcome.FAILED
        }
        return GoogleConsumeOutcome.CONSUMED
    }

    companion object {
        const val FAILURE_METRIC = "gamss.payment.google.consume.failure"
    }
}

/** consume 을 한 번 시도한 결과. 어느 실패든 `consumed_at` 은 비어 있고 스케줄러가 다시 본다. */
enum class GoogleConsumeOutcome {
    /** consume 이 끝났거나 이미 끝나 있었고, 기록까지 남겼다. */
    CONSUMED,

    /** 이 구매만의 실패다. Google 이 요청을 거부했거나, consume 은 됐는데 기록하지 못했다. */
    FAILED,

    /** Google 과 통신할 수 없다. 다른 구매도 같은 Google 을 부르므로 스케줄러는 그 회차를 멈춘다. */
    STORE_UNAVAILABLE,
}
