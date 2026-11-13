package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.google.GooglePlayClient
import com.nexters.gamss.payment.google.GoogleVoidedPurchase
import com.nexters.gamss.payment.repository.GoogleRefundRecordRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Voided Purchases API 로 Google 환불을 대사한다. RTDN 은 누락될 수 있으므로 이 배치를 기준으로 삼는다.
 *
 * 하루 한 번, 마지막 성공 시각부터 지금까지를 조회한다. 실패하면 마지막 성공 시각을 갱신하지 않아 다음 실행이 빠진
 * 구간부터 다시 본다. 조회 범위는 최근 [MAX_LOOKBACK] 까지라 그보다 오래 실패하면 그 앞은 볼 수 없다. 늦게 기록된
 * 환불을 놓치지 않도록 [OVERLAP] 만큼 겹쳐 조회하고, 이미 처리한 항목은 처리 기록으로 거른다.
 *
 * 환불 출처는 `voided:{voidedTimeMillis}` 다. `orderId` 는 일회성 구매에 하나뿐이라 부분 환불마다 같은 값이 와서
 * 출처로 쓸 수 없다. 웹훅이 이미 회수한 환불이면 목표가 같아 차이 0 으로 끝난다.
 *
 * 항목 하나의 예상하지 못한 오류가 같은 회차의 나머지 환불을 막지 않도록 항목마다 예외를 잡고, 끝까지 처리한 뒤
 * 회차를 실패로 끝낸다. 실패한 항목은 기록하지 않아 다음 회차가 다시 본다. Google 과 통신할 수 없을 때만 그 자리에서
 * 멈춘다. 다른 항목도 같은 Google 을 부르기 때문이다.
 *
 * 실패는 로그만으로는 경보가 울리지 않으므로 지표로 드러낸다. 마지막 성공 시각 게이지([LAST_SUCCESS_METRIC])가 오래되면
 * 'Google 환불 대사 중단' 경보가 울린다. 30일이 지나면 Google 이 그 환불을 목록에서 빼므로 그 전에 고쳐야 한다.
 */
@Component
class GoogleVoidedPurchaseReconciler(
    private val client: GooglePlayClient,
    private val reclaimer: GoogleRefundReclaimer,
    private val recordRepository: GoogleRefundRecordRepository,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 마지막으로 성공한 시각(epoch 초). 기동 직후 저장된 체크포인트로 덮어쓰고, 기록이 없으면 기동 시각으로 둔다.
     * 체크포인트가 있는데 기동 시각으로 두면 배포할 때마다 나이가 0 이 돼 계속 실패하는 배치를 가린다.
     */
    private val lastSuccessEpochSeconds = AtomicLong(Instant.now().epochSecond)

    /** 회차가 실패한 횟수. 한 번도 성공하지 못한 채 배포가 잦아 게이지가 계속 기동 시각으로 바뀌는 경우까지 잡는다. */
    private val failureCounter =
        Counter
            .builder(FAILURE_METRIC)
            .description("Google 환불 대사 회차가 실패한 횟수. 실패한 구간은 다음 회차가 다시 조회한다")
            .register(meterRegistry)

    init {
        // 자격증명이 없는 환경(Android 결제를 받지 않는 prod 등)에서는 배치가 돌지 않으므로 게이지를 두지 않는다.
        // 게이지가 있으면 기동 시각에서 멈춰 '대사 중단' 경보가 울린다.
        if (client.configured) {
            Gauge
                .builder(LAST_SUCCESS_METRIC, lastSuccessEpochSeconds) { it.get().toDouble() }
                .description("Google 환불 대사가 마지막으로 성공한 시각(epoch 초)")
                .register(meterRegistry)
        }
    }

    @EventListener(ApplicationReadyEvent::class)
    fun loadLastSuccess() {
        // 여기서 예외가 나가면 기동이 실패한다. 게이지를 맞추는 일이 기동을 좌우할 일은 아니다.
        runCatching { recordRepository.lastSucceededAt(JOB_NAME) }
            .onSuccess { it?.let { lastSuccess -> lastSuccessEpochSeconds.set(lastSuccess.epochSecond) } }
            .onFailure { log.warn("Google 환불 대사의 마지막 성공 시각을 읽지 못했습니다. 게이지는 기동 시각으로 둡니다", it) }
    }

    @Scheduled(cron = CRON, zone = ZONE_ID)
    fun reconcile() {
        runCatching { reconcile(Instant.now()) }
            .onFailure {
                failureCounter.increment()
                log.error("Google 환불 대사 실패(무시). 다음 회차가 빠진 구간부터 다시 본다", it)
            }
    }

    /**
     * 성공하면 [now] 를 마지막 성공 시각으로 저장한다. Google 과 통신할 수 없으면 그 자리에서, 다른 항목이 실패하면
     * 나머지를 끝까지 처리한 뒤 예외로 끝난다.
     */
    fun reconcile(now: Instant) {
        if (!client.configured) {
            return
        }
        val earliest = now.minus(MAX_LOOKBACK)
        val lastSucceededAt = recordRepository.lastSucceededAt(JOB_NAME)
        val start = lastSucceededAt?.minus(OVERLAP)?.coerceAtLeast(earliest) ?: earliest
        if (lastSucceededAt != null && lastSucceededAt.minus(OVERLAP).isBefore(earliest)) {
            log.error("마지막 대사 성공({})이 조회 가능 범위보다 오래돼 그 사이 환불을 볼 수 없습니다. 수동 확인이 필요합니다.", lastSucceededAt)
        }
        var pageToken: String? = null
        var handled = 0
        var failed = 0
        do {
            val page = client.listVoidedPurchases(start, now, pageToken)
            for (item in page.items) {
                try {
                    if (handle(item, now)) {
                        handled++
                    }
                } catch (e: BusinessException) {
                    // handle() 이 올리는 BusinessException 은 PAYMENT_STORE_UNAVAILABLE 뿐이다. 회차를 멈춘다.
                    throw e
                } catch (e: RuntimeException) {
                    failed++
                    log.error(
                        "Google 환불 대사 항목을 처리하지 못했습니다. 다음 회차에 다시 봅니다: key={}, orderId={}",
                        keyOf(item),
                        item.orderId,
                        e,
                    )
                }
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        check(failed == 0) { "Google 환불 대사 항목 ${failed}건을 처리하지 못해 마지막 성공 시각을 갱신하지 않습니다." }
        recordRepository.saveLastSucceededAt(JOB_NAME, now)
        lastSuccessEpochSeconds.set(now.epochSecond)
        log.info("Google 환불 대사 완료: from={}, to={}, 처리={}건", start, now, handled)
    }

    /** 새로 처리했으면 true. Google 장애와 예상하지 못한 오류는 그대로 올린다. */
    private fun handle(
        item: GoogleVoidedPurchase,
        now: Instant,
    ): Boolean {
        val key = keyOf(item)
        val sourceId = "$SOURCE_PREFIX${item.voidedTime.toEpochMilli()}"
        if (recordRepository.isVoidedPurchaseHandled(key, sourceId)) {
            return false
        }
        val result =
            try {
                reclaimer.reclaim(item.purchaseToken, partial = item.voidedQuantity != null, sourceId = sourceId)
            } catch (e: BusinessException) {
                when (e.errorCode) {
                    ErrorCode.PAYMENT_STORE_UNAVAILABLE -> {
                        throw e
                    }

                    // 결제 기록이 없다. 대부분 지급 전에 환불된 구매(Pending 미완료, 지급 거부)라 회수할 토큰이 없다. 다만 지급
                    // 검증과 커밋 사이에 환불이 기록됐다면 지급이 조금 늦게 생긴다. 처리 기록을 남기지 않으면 다음 회차가
                    // 겹친 구간(OVERLAP)에서 이 항목을 다시 보고, 그때 생긴 결제를 회수한다.
                    ErrorCode.PAYMENT_NOT_FOUND -> {
                        warnPaymentNotFound(item, key)
                        return false
                    }

                    // 회수량을 정할 수 없다. 다시 해도 같으므로 수동 처리 대상으로 남기고 넘어간다.
                    else -> {
                        log.error(
                            "Google 환불을 회수하지 못했습니다. 수동 처리가 필요합니다: key={}, orderId={}, voidedReason={}, error={}",
                            key,
                            item.orderId,
                            item.voidedReason,
                            e.errorCode,
                        )
                        null
                    }
                }
            }
        if (result != null && item.voidedReason == VOIDED_REASON_UNACKNOWLEDGED) {
            // 지급한 결제가 3일 안에 consume(=acknowledge)되지 않아 Google 이 자동 환불했다. consume 재시도가 실패했다는 뜻이다.
            // 결제가 있을 때만 남긴다. 결제가 없는 미승인 환불은 지급하지 않은 구매라 consume 과 관계가 없다.
            log.error(
                "확인(consume)하지 못해 Google 이 자동 환불한 결제입니다. consume 재시도 기록을 확인하세요: " +
                    "paymentId={}, key={}, orderId={}, changedTokens={}",
                result.paymentId,
                key,
                item.orderId,
                result.changedTokens,
            )
        }
        logHandled(item, key)
        recordRepository.markVoidedPurchaseHandled(key, sourceId, item.voidedSource, item.voidedReason, now)
        return true
    }

    private fun warnPaymentNotFound(
        item: GoogleVoidedPurchase,
        key: String,
    ) {
        if (item.voidedReason == VOIDED_REASON_UNACKNOWLEDGED) {
            log.warn(
                "이 환경에서 지급한 기록이 없는 구매가 확인되지 않아 자동 환불됐습니다(지급 거부, 앱·RTDN 지급 누락, 다른 환경의 구매 중 하나). " +
                    "다음 회차에 한 번 더 봅니다: key={}, orderId={}",
                key,
                item.orderId,
            )
        } else {
            log.warn("환불된 Google 구매의 결제 기록이 없습니다. 다음 회차에 한 번 더 봅니다: key={}, orderId={}", key, item.orderId)
        }
    }

    /** 지불 거절·사기 사유는 수수료가 붙거나 남용일 수 있어 WARN 으로 남긴다. 오래 남는 근거는 처리 기록의 사유 컬럼이다. */
    private fun logHandled(
        item: GoogleVoidedPurchase,
        key: String,
    ) {
        val message = "Google 환불 대사 항목을 처리했습니다: key={}, orderId={}, voidedSource={}, voidedReason={}, voidedQuantity={}"
        val args = arrayOf<Any?>(key, item.orderId, item.voidedSource, item.voidedReason, item.voidedQuantity)
        if (item.voidedReason in ABUSE_REASONS) {
            log.warn(message, *args)
        } else {
            log.info(message, *args)
        }
    }

    private fun keyOf(item: GoogleVoidedPurchase): String = StoreTransactionKey.ofGooglePurchaseToken(item.purchaseToken).value

    companion object {
        const val JOB_NAME = "google-voided-purchase"
        const val LAST_SUCCESS_METRIC = "gamss.payment.google.voided.reconcile.last.success.timestamp.seconds"
        const val FAILURE_METRIC = "gamss.payment.google.voided.reconcile.failure"
        private const val ZONE_ID = "Asia/Seoul"

        /** 다른 새벽 작업(3:30 쿼터 정리)과 겹치지 않게 둔다. */
        private const val CRON = "0 10 4 * * *"
        private const val SOURCE_PREFIX = "voided:"

        /** voidedReason 8: 미승인 구매(3일 안에 확인되지 않아 자동 환불). */
        private const val VOIDED_REASON_UNACKNOWLEDGED = 8

        /** voidedReason 5 사기, 6 친구 사기(가족·친구가 허락 없이 결제), 7 지불 거절. */
        private val ABUSE_REASONS = setOf(5, 6, 7)

        /** Voided Purchases API 가 조회를 허용하는 범위. 경계에서 거부되지 않게 조금 줄인다. */
        val MAX_LOOKBACK: Duration = Duration.ofDays(30).minusHours(1)

        val OVERLAP: Duration = Duration.ofDays(1)
    }
}
