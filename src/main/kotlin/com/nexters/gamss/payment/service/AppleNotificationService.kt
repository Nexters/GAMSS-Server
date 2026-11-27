package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.apple.AppleNotificationDecoder
import com.nexters.gamss.payment.apple.InvalidAppleNotificationException
import com.nexters.gamss.payment.repository.AppleNotificationRecordRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * App Store Server Notifications V2 를 처리한다. 웹훅과 알림 기록 배치가 같은 `signedPayload` 를 넘긴다.
 *
 * 처리했거나 처리할 필요가 없으면 [AppleNotificationOutcome.ACK] 이고 그때만 UUID 를 기록한다. 실패한 알림을 기록하면
 * 재전송과 배치가 다시 처리하지 못한다. Apple 과 통신할 수 없거나 거래 서명을 확인하지 못하면 다시 받고(RETRY), 그 밖의
 * 실패(주인을 모르는 구매, 판매 중이 아닌 상품)는 다시 받아도 같으므로 로그를 남기고 ACK 한다. 예상하지 못한 오류는
 * 그대로 올려 5xx 로 다시 받는다.
 *
 * 서명이 틀린 알림은 [AppleNotificationOutcome.REJECTED] 이고 [REJECTED_METRIC] 을 올린다. 인증 없이 열린 경로라
 * 위조 시도이거나 우리 설정(bundleId, 루트 인증서) 문제다.
 */
@Service
class AppleNotificationService(
    private val decoder: AppleNotificationDecoder,
    private val handlers: AppleNotificationHandlers,
    private val recordRepository: AppleNotificationRecordRepository,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val rejectedCounter =
        Counter
            .builder(REJECTED_METRIC)
            .description("서명을 확인할 수 없어 거부한 App Store 알림 수")
            .register(meterRegistry)

    fun handle(signedPayload: String): AppleNotificationOutcome {
        val notification =
            try {
                decoder.decode(signedPayload)
            } catch (e: InvalidAppleNotificationException) {
                rejectedCounter.increment()
                log.warn("서명을 확인할 수 없는 App Store 알림이라 거부했습니다: {}", e.message)
                return AppleNotificationOutcome.REJECTED
            }
        val uuid = notification.notificationUuid
        if (recordRepository.isNotificationHandled(uuid)) {
            return AppleNotificationOutcome.ACK
        }
        try {
            if (!handlers.handle(notification)) {
                log.info("처리하지 않는 App Store 알림이라 기록만 합니다: notificationUuid={}, type={}", uuid, notification.type)
            }
        } catch (e: BusinessException) {
            if (e.errorCode == ErrorCode.PAYMENT_STORE_UNAVAILABLE) {
                log.warn("App Store 알림을 지금 처리할 수 없어 다시 받습니다: notificationUuid={}, type={}", uuid, notification.type)
                return AppleNotificationOutcome.RETRY
            }
            log.warn(
                "App Store 알림을 처리하지 못했지만 다시 받아도 같은 결과라 처리한 것으로 둡니다: notificationUuid={}, type={}, error={}",
                uuid,
                notification.type,
                e.errorCode,
            )
        }
        recordRepository.markNotificationHandled(uuid, notification.type, notification.subtype, Instant.now())
        return AppleNotificationOutcome.ACK
    }

    companion object {
        const val REJECTED_METRIC = "gamss.payment.apple.notification.rejected"
    }
}

enum class AppleNotificationOutcome {
    /** 처리했거나 처리할 필요가 없다. 200 으로 응답한다. */
    ACK,

    /** 일시적인 실패라 다시 받아야 한다. 5xx 로 응답한다. */
    RETRY,

    /** 서명을 확인할 수 없다. 4xx 로 응답한다. */
    REJECTED,
}
