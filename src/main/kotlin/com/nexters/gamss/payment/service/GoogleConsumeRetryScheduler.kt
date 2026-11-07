package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.domain.Payment
import com.nexters.gamss.payment.domain.PaymentEnvironment
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.google.GoogleConsumptionState
import com.nexters.gamss.payment.google.GooglePlayClient
import com.nexters.gamss.payment.google.GooglePurchaseState
import com.nexters.gamss.payment.repository.PaymentRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * 지급했지만 consume 하지 못한 Google 결제를 주기적으로 다시 consume 한다.
 *
 * 앱 요청 경로의 consume 은 실패해도 성공으로 응답하고 `consumed_at = NULL` 로 남긴다. 그대로 두면 3일 뒤
 * Google 이 자동 환불하는데 토큰은 이미 지급돼 있다. 그래서 여기서 구매를 다시 조회해 상태별로 처리한다.
 * - 이미 consume 됨: 기록만 채운다.
 * - PURCHASED 이고 아직 consume 전: consume 을 다시 시도한다.
 * - CANCELLED: consume 할 수 없다. 회수는 RTDN·대사 배치(#246)가 맡으므로 WARN 만 남긴다.
 *
 * **대상은 구매 후 [CONSUME_WINDOW] 안의 결제로 한정한다.** 그보다 오래된 구매는 이미 자동 환불돼 consume
 * 할 수 없고, 상태를 남길 컬럼도 없어서 조건이 없으면 같은 행을 영원히 다시 조회한다. 막 만든 행은
 * 앱 요청 경로가 consume 하고 있을 수 있어 [FRESH_ROW_GRACE] 만큼 건너뛴다.
 *
 * 결제 한 건의 실패가 나머지를 막지 않도록 건마다 예외를 삼킨다. 한 회차에 [BATCH_SIZE] 건까지만 본다.
 *
 * **이 작업은 다른 `@Scheduled` 작업과 스케줄링 스레드 하나를 나눠 쓴다.** Google 호출 한 번은 본 요청
 * 타임아웃(연결 5초·읽기 10초)에 더해, 토큰 갱신이 끼면 라이브러리 기본 타임아웃(최대 약 40초)을 따른다
 * ([com.nexters.gamss.payment.google.GooglePlayConfig]). 결제마다 그만큼 기다리면 Google 장애 동안 도메인
 * 지표 갱신과 PENDING 정리가 함께 멈춘다. 그래서 조회든 consume 이든 Google 과 통신할 수 없다는 오류가
 * 나면 모든 결제가 같은 Google 을 보므로 그 회차를 멈춘다. 또 새 결제를 시작하기 전에 회차가 [ROUND_BUDGET] 을
 * 넘겼는지 보고, 넘겼으면 멈춘다. 남은 결제는 다음 회차에 다시 본다. 오래된 결제부터 보므로 48시간 경보 대상은 앞에서 처리된다.
 */
@Component
class GoogleConsumeRetryScheduler(
    private val client: GooglePlayClient,
    private val consumer: GooglePurchaseConsumer,
    private val paymentRepository: PaymentRepository,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 구매 후 [STALE_AFTER] 가 지나도 consume 기록이 없는 결제를 재시도 회차에서 본 횟수. Google 이 PURCHASED 로
     * 확인한 결제만 세는 것이 아니다. Google 장애나 회차 시간 상한 때문에 상태를 보지 못한 결제, PENDING 처럼
     * 상태가 예상과 다른 결제, 조회나 consume 이 거부된 결제도 센다. 취소로 확인된 결제와 테스트 구매는 세지 않는다.
     * 회차마다 남아 있는 건수를 더하므로 같은 결제가 여러 번 세어진다. 늘어나면 자동 환불 전에 사람이 봐야 한다는
     * 뜻이다. 처음부터 0 으로 등록해야 처음 생긴 증가도 increase() 가 센다.
     */
    private val staleCounter =
        Counter
            .builder(STALE_METRIC)
            .description("구매 후 48시간이 지나도 consume 되지 않은 Google 결제를 재시도 회차에서 본 횟수")
            .register(meterRegistry)

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    fun retry() {
        retry(Instant.now())
    }

    /** [now] 와 [roundBudget] 을 받는 것은 테스트가 시각과 시간 상한을 정하려는 것이다. */
    fun retry(
        now: Instant,
        roundBudget: Duration = ROUND_BUDGET,
    ) {
        if (!client.configured) {
            return
        }
        val targets =
            paymentRepository.findUnconsumed(
                platform = PaymentPlatform.GOOGLE,
                purchasedAfter = now.minus(CONSUME_WINDOW),
                createdBefore = now.minus(FRESH_ROW_GRACE),
                pageable = PageRequest.of(0, BATCH_SIZE),
            )
        val startedAt = System.nanoTime()
        for ((index, payment) in targets.withIndex()) {
            // 첫 결제는 상한과 관계없이 본다. 회차마다 적어도 한 건은 앞으로 나아간다.
            if (index > 0 && Duration.ofNanos(System.nanoTime() - startedAt) > roundBudget) {
                stopRound("회차 시간 상한을 넘었습니다", targets.drop(index), now)
                return
            }
            try {
                retryOne(payment, now)
            } catch (e: BusinessException) {
                reportIfStale(payment, now)
                if (e.errorCode == ErrorCode.PAYMENT_STORE_UNAVAILABLE) {
                    stopRound("Google 과 통신할 수 없습니다", targets.drop(index + 1), now)
                    return
                }
                log.warn("consume 재시도 중 오류가 났습니다. 다음 회차에 다시 시도합니다: paymentId={}, error={}", payment.id, e.errorCode)
            } catch (e: RuntimeException) {
                log.warn("consume 재시도 중 오류가 났습니다. 다음 회차에 다시 시도합니다: paymentId={}", payment.id, e)
                reportIfStale(payment, now)
            }
        }
    }

    /** 남은 결제는 Google 을 부르지 않고 넘긴다. 다만 48시간 경보는 Google 장애 중에도 울려야 하므로 센다. */
    private fun stopRound(
        reason: String,
        remaining: List<Payment>,
        now: Instant,
    ) {
        log.warn("{}. consume 재시도 회차를 멈추고 남은 {}건은 다음 회차에 다시 봅니다.", reason, remaining.size)
        remaining.forEach { reportIfStale(it, now) }
    }

    private fun retryOne(
        payment: Payment,
        now: Instant,
    ) {
        val purchaseToken = payment.storePurchaseToken
        if (purchaseToken.isNullOrBlank()) {
            log.error(
                "purchaseToken 원문이 없어 consume 할 수 없습니다. 수동 처리가 필요합니다: paymentId={}, storeOrderId={}",
                payment.id,
                payment.storeOrderId,
            )
            return
        }
        val purchase = client.getPurchase(purchaseToken)
        val consumptionState = purchase.lineItems.singleOrNull()?.consumptionState
        when {
            consumptionState == GoogleConsumptionState.CONSUMED -> {
                consumer.consume(payment.id, payment.productId, purchaseToken, alreadyConsumed = true)
            }

            purchase.purchaseState == GooglePurchaseState.CANCELLED -> {
                log.warn("취소된 Google 구매라 consume 하지 않습니다. 회수는 #246 이 맡습니다: paymentId={}, storeOrderId={}", payment.id, payment.storeOrderId)
                return
            }

            purchase.purchaseState == GooglePurchaseState.PURCHASED -> {
                when (consumer.consume(payment.id, payment.productId, purchaseToken, alreadyConsumed = false)) {
                    GoogleConsumeOutcome.CONSUMED -> Unit

                    GoogleConsumeOutcome.FAILED -> reportIfStale(payment, now)

                    // 조회 장애와 같은 길로 보낸다. retry 의 catch 가 적체를 세고 회차를 멈춘다.
                    GoogleConsumeOutcome.STORE_UNAVAILABLE -> throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
                }
            }

            else -> {
                log.warn(
                    "consume 재시도 대상의 Google 구매 상태가 예상과 다릅니다: paymentId={}, purchaseState={}, consumptionState={}",
                    payment.id,
                    purchase.purchaseState,
                    consumptionState,
                )
                reportIfStale(payment, now)
            }
        }
    }

    /**
     * 테스트 구매(라이선스 테스터)는 3분 안에 확인되지 않으면 환불되므로 경보에서 뺀다. 남겨 두면 dev 에서
     * 테스트 결제 하나가 실패할 때마다 경보가 울린다.
     */
    private fun reportIfStale(
        payment: Payment,
        now: Instant,
    ) {
        if (payment.environment == PaymentEnvironment.SANDBOX) {
            return
        }
        if (payment.purchasedAt.isBefore(now.minus(STALE_AFTER))) {
            staleCounter.increment()
            log.error(
                "구매 후 48시간이 지나도 consume 하지 못했습니다. 3일이 지나면 Google 이 자동 환불합니다: paymentId={}, storeOrderId={}, purchasedAt={}",
                payment.id,
                payment.storeOrderId,
                payment.purchasedAt,
            )
        }
    }

    companion object {
        const val STALE_METRIC = "gamss.payment.google.unconsumed.stale"
        private const val INTERVAL_MILLIS = 10 * 60 * 1000L
        private const val BATCH_SIZE = 50

        /** Google 이 확인되지 않은 구매를 자동 환불하는 기한. 이보다 오래된 구매는 consume 할 수 없다. */
        val CONSUME_WINDOW: Duration = Duration.ofDays(3)

        val STALE_AFTER: Duration = Duration.ofHours(48)

        val FRESH_ROW_GRACE: Duration = Duration.ofMinutes(5)

        /**
         * 이 시간이 지나면 새 결제를 시작하지 않는다. 결제를 시작하기 전에만 보므로, 회차는 이보다 결제 한 건(Google
         * 호출 최대 두 번)만큼 더 걸릴 수 있다. 정상이면 50건이 몇 초 안에 끝난다.
         */
        val ROUND_BUDGET: Duration = Duration.ofMinutes(2)
    }
}
