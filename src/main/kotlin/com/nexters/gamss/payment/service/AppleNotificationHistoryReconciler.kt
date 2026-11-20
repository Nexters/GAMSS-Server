package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.apple.AppleServerApiClient
import com.nexters.gamss.payment.repository.PaymentJobCheckpointRepository
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
 * Get Notification History 로 놓친 App Store 알림을 다시 처리한다. 웹훅이 끝내 실패했거나 서버가 내려가 있던 동안의 알림이
 * 대상이다. Sandbox 는 Apple 이 다시 보내지 않으므로 dev 에서는 이 배치가 유일한 복구 경로다.
 *
 * 하루 한 번, 마지막 성공 시각부터 지금까지를 조회한다. 실패 건만이 아니라 전체를 받아 처리 기록(UUID)으로 거른다. 우리가
 * 200 으로 답했어도 처리 기록을 남기지 않은 알림(처리 도중 서버 종료 등)이 있을 수 있고, 소모성 상품이라 양이 적다.
 * [GoogleVoidedPurchaseReconciler] 와 같은 방식으로 [OVERLAP] 만큼 겹쳐 조회하고, 실패한 회차는 마지막 성공 시각을 갱신하지 않는다.
 *
 * 항목마다 [AppleNotificationService] 에 그대로 넘긴다. Apple 과 통신할 수 없으면 그 자리에서 멈추고, 다른 실패는 끝까지
 * 처리한 뒤 회차를 실패로 끝낸다. 실패는 지표로 드러내 경보가 울리게 한다([LAST_SUCCESS_METRIC], [FAILURE_METRIC]).
 */
@Component
class AppleNotificationHistoryReconciler(
    private val client: AppleServerApiClient,
    private val notificationService: AppleNotificationService,
    private val checkpointRepository: PaymentJobCheckpointRepository,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 마지막으로 성공한 시각(epoch 초). 기동 직후 저장된 값으로 덮어쓴다. 이유는 [GoogleVoidedPurchaseReconciler] 와 같다. */
    private val lastSuccessEpochSeconds = AtomicLong(Instant.now().epochSecond)

    private val failureCounter =
        Counter
            .builder(FAILURE_METRIC)
            .description("App Store 알림 기록 대사 회차가 실패한 횟수. 실패한 구간은 다음 회차가 다시 조회한다")
            .register(meterRegistry)

    init {
        // 키가 없어 배치가 돌지 않는 환경에 게이지가 있으면 기동 시각에서 멈춰 '대사 중단' 경보가 울린다.
        if (client.configured) {
            Gauge
                .builder(LAST_SUCCESS_METRIC, lastSuccessEpochSeconds) { it.get().toDouble() }
                .description("App Store 알림 기록 대사가 마지막으로 성공한 시각(epoch 초)")
                .register(meterRegistry)
        }
    }

    @EventListener(ApplicationReadyEvent::class)
    fun loadLastSuccess() {
        runCatching { checkpointRepository.lastSucceededAt(JOB_NAME) }
            .onSuccess { it?.let { lastSuccess -> lastSuccessEpochSeconds.set(lastSuccess.epochSecond) } }
            .onFailure { log.warn("App Store 알림 기록 대사의 마지막 성공 시각을 읽지 못했습니다. 게이지는 기동 시각으로 둡니다", it) }
    }

    @Scheduled(cron = CRON, zone = ZONE_ID)
    fun reconcile() {
        runCatching { reconcile(Instant.now()) }
            .onFailure {
                failureCounter.increment()
                log.error("App Store 알림 기록 대사 실패(무시). 다음 회차가 빠진 구간부터 다시 본다", it)
            }
    }

    fun reconcile(now: Instant) {
        if (!client.configured) {
            return
        }
        val earliest = now.minus(MAX_LOOKBACK)
        val lastSucceededAt = checkpointRepository.lastSucceededAt(JOB_NAME)
        val start = lastSucceededAt?.minus(OVERLAP)?.coerceAtLeast(earliest) ?: earliest
        if (lastSucceededAt != null && lastSucceededAt.minus(OVERLAP).isBefore(earliest)) {
            log.error("마지막 대사 성공({})이 조회 가능 범위보다 오래돼 그 사이 알림을 볼 수 없습니다. 수동 확인이 필요합니다.", lastSucceededAt)
        }
        var paginationToken: String? = null
        var seen = 0
        var failed = 0
        do {
            val page = client.notificationHistory(start, now, paginationToken)
            for (signedPayload in page.signedPayloads) {
                seen++
                if (!handle(signedPayload)) {
                    failed++
                }
            }
            paginationToken = page.nextPaginationToken
        } while (paginationToken != null)
        check(failed == 0) { "App Store 알림 ${failed}건을 처리하지 못해 마지막 성공 시각을 갱신하지 않습니다." }
        checkpointRepository.saveLastSucceededAt(JOB_NAME, now)
        lastSuccessEpochSeconds.set(now.epochSecond)
        log.info("App Store 알림 기록 대사 완료: from={}, to={}, 조회={}건", start, now, seen)
    }

    /** 처리했으면 true. 다시 받아야 하는 실패면 회차를 멈추고, 그 밖의 실패는 false 로 넘어간다. */
    private fun handle(signedPayload: String): Boolean =
        try {
            when (notificationService.handle(signedPayload)) {
                AppleNotificationOutcome.ACK -> {
                    true
                }

                AppleNotificationOutcome.RETRY -> {
                    throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
                }

                AppleNotificationOutcome.REJECTED -> {
                    // Apple 이 직접 돌려준 알림이라 위조일 수 없다. 루트 인증서나 bundleId 설정을 의심한다.
                    log.error("App Store 알림 기록의 알림 서명을 확인하지 못했습니다. 검증 설정을 확인하세요.")
                    false
                }
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: RuntimeException) {
            log.error("App Store 알림 기록 항목을 처리하지 못했습니다. 다음 회차에 다시 봅니다", e)
            false
        }

    companion object {
        const val JOB_NAME = "apple-notification-history"
        const val LAST_SUCCESS_METRIC = "gamss.payment.apple.notification.reconcile.last.success.timestamp.seconds"
        const val FAILURE_METRIC = "gamss.payment.apple.notification.reconcile.failure"
        private const val ZONE_ID = "Asia/Seoul"

        /** Google 환불 대사(04:10)와 겹치지 않게 둔다. */
        private const val CRON = "0 20 4 * * *"

        /** Get Notification History 가 허용하는 범위(180일). 경계에서 거부되지 않게 조금 줄인다. */
        val MAX_LOOKBACK: Duration = Duration.ofDays(180).minusHours(1)

        val OVERLAP: Duration = Duration.ofDays(1)
    }
}
