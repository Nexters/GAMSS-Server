package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.domain.GoogleNotificationKind
import com.nexters.gamss.payment.google.GoogleDeveloperNotification
import com.nexters.gamss.payment.google.GooglePlayProperties
import com.nexters.gamss.payment.google.OneTimeProductNotification
import com.nexters.gamss.payment.google.PendingRefundReviewNotification
import com.nexters.gamss.payment.google.VoidedPurchaseNotification
import com.nexters.gamss.payment.repository.GoogleRefundRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Base64

/**
 * Google Play RTDN 을 처리한다.
 *
 * 구매 알림과 부분 환불 알림은 Google 을 다시 조회한 결과로 판단한다. 전액 환불(refundType = 1)은 이슈와 RTDN
 * 레퍼런스대로 알림의 purchaseToken 으로 찾은 결제를 조회 없이 전액 회수한다. 그 본문이 진짜라는 근거는 push 인증
 * (OIDC 토큰의 aud·email)과 RTDN 토픽의 게시 권한이다. 그래서 토픽 Publisher 는 Google Play 서비스 계정에만 주고,
 * push 서비스 계정의 토큰을 만들 수 있는 주체도 Pub/Sub 서비스 에이전트로 한정해야 한다. 대사 배치는 Google 이 돌려준
 * 환불 목록으로 같은 결제를 다시 맞춘다.
 *
 * 결과가 [GoogleNotificationOutcome.RETRY] 면 Pub/Sub 이 지수 백오프로 다시 보낸다. Google 과 통신할 수 없을 때만
 * 그렇게 한다. 그 밖의 실패(주인을 모르는 구매, 결제 기록이 없는 환불)는 다시 보내도 같은 결과라 ERROR·WARN 으로
 * 남기고 ack 한다. 환불은 대사 배치가 하루 한 번 다시 맞춘다.
 *
 * 지불 거절 검토 요청(pendingRefundReviewNotification)에는 응답하지 않는다. 종류를 따로 기록하고 주문번호와 사유만
 * WARN 으로 남긴다. 지불 거절이 확정되면 대사 배치가 voided(사유 7)로 회수한다.
 */
@Service
class GoogleNotificationService(
    private val properties: GooglePlayProperties,
    private val objectMapper: ObjectMapper,
    private val verifier: GooglePurchaseVerifier,
    private val grantService: PurchaseGrantService,
    private val consumer: GooglePurchaseConsumer,
    private val reclaimer: GoogleRefundReclaimer,
    private val recordRepository: GoogleRefundRecordRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(
        messageId: String,
        data: String?,
    ): GoogleNotificationOutcome {
        if (recordRepository.isNotificationHandled(messageId)) {
            return GoogleNotificationOutcome.ACK
        }
        val notification = decode(messageId, data) ?: return GoogleNotificationOutcome.ACK
        if (notification.packageName != properties.packageName) {
            log.warn("다른 앱의 RTDN 이라 무시합니다: messageId={}, packageName={}", messageId, notification.packageName)
            return GoogleNotificationOutcome.ACK
        }
        val kind = kindOf(notification)
        val outcome =
            try {
                dispatch(messageId, notification)
                GoogleNotificationOutcome.ACK
            } catch (e: BusinessException) {
                if (e.errorCode == ErrorCode.PAYMENT_STORE_UNAVAILABLE) {
                    log.warn("Google 과 통신할 수 없어 RTDN 을 다시 받습니다: messageId={}, kind={}", messageId, kind)
                    GoogleNotificationOutcome.RETRY
                } else {
                    log.warn("RTDN 을 처리하지 못했지만 다시 받아도 같은 결과라 ack 합니다: messageId={}, kind={}, error={}", messageId, kind, e.errorCode)
                    GoogleNotificationOutcome.ACK
                }
            }
        if (outcome == GoogleNotificationOutcome.ACK) {
            recordRepository.markNotificationHandled(messageId, kind, Instant.now())
        }
        return outcome
    }

    private fun dispatch(
        messageId: String,
        notification: GoogleDeveloperNotification,
    ) {
        notification.oneTimeProductNotification?.let { return handleOneTimeProduct(messageId, it) }
        notification.voidedPurchaseNotification?.let { return handleVoided(messageId, it) }
        notification.pendingRefundReviewNotification?.let { return handlePendingRefundReview(messageId, notification, it) }
        if (notification.testNotification != null) {
            log.info("RTDN 테스트 알림을 받았습니다: messageId={}", messageId)
            return
        }
        log.info("처리하지 않는 RTDN 이라 기록만 합니다: messageId={}", messageId)
    }

    private fun handleOneTimeProduct(
        messageId: String,
        notification: OneTimeProductNotification,
    ) {
        val token = notification.purchaseToken
        if (token.isNullOrBlank()) {
            log.error("일회성 상품 RTDN 에 purchaseToken 이 없습니다: messageId={}", messageId)
            return
        }
        when (notification.notificationType) {
            ONE_TIME_PRODUCT_PURCHASED -> grantFromNotification(messageId, token)

            // Pending 결제가 끝내 완료되지 않은 경우 등이다. 지급된 구매가 취소되면 voided 알림과 대사 배치가 회수한다.
            ONE_TIME_PRODUCT_CANCELED -> log.info("일회성 상품 구매 취소 알림입니다. 회수는 voided 처리가 맡습니다: messageId={}", messageId)

            else -> log.info("처리하지 않는 일회성 상품 알림입니다: messageId={}, type={}", messageId, notification.notificationType)
        }
    }

    /**
     * 앱이 지급 요청을 보내지 못한 구매(앱 삭제, 나중에 완료된 Pending)를 지급하고 consume 한다. 이미 지급된 구매면
     * 지급은 재요청으로 끝나고 consume 만 다시 본다. 계정 토큰이 없거나 모르는 값이면 지급 쪽이 로그를 남기고 거부한다.
     *
     * 앱 요청과 거의 동시에 오는 일이 흔해 두 경로가 같은 구매를 consume 할 수 있다. 먼저 끝난 consume 은
     * [GooglePurchaseConsumer] 가 다시 조회해 실패로 세지 않는다.
     */
    private fun grantFromNotification(
        messageId: String,
        purchaseToken: String,
    ) {
        val verification =
            try {
                verifier.verify(purchaseToken, null)
            } catch (e: BusinessException) {
                if (e.errorCode == ErrorCode.PURCHASE_CANCELLED) {
                    log.info("구매 알림이 왔지만 이미 취소된 구매입니다: messageId={}", messageId)
                    return
                }
                throw e
            }
        when (verification) {
            is GooglePurchaseVerification.Pending -> {
                log.info("구매 알림이 왔지만 아직 결제 대기 중입니다: messageId={}", messageId)
            }

            is GooglePurchaseVerification.Verified -> {
                val purchase = verification.purchase
                val grant = grantService.grantFromStoreNotification(purchase)
                if (!grant.replayed) {
                    log.info("앱 요청 없이 RTDN 으로 지급했습니다: paymentId={}, storeOrderId={}", grant.paymentId, purchase.storeOrderId)
                }
                try {
                    consumer.consume(grant.paymentId, purchase.productId, purchaseToken, verification.consumed)
                } catch (e: RuntimeException) {
                    // 지급은 커밋됐다. consume 은 재시도 스케줄러가 다시 본다.
                    log.error("consume 처리 중 예상하지 못한 오류가 났습니다. 재시도 스케줄러가 다시 시도합니다: paymentId={}", grant.paymentId, e)
                }
            }
        }
    }

    private fun handleVoided(
        messageId: String,
        notification: VoidedPurchaseNotification,
    ) {
        val token = notification.purchaseToken
        if (notification.productType != PRODUCT_TYPE_ONE_TIME || token.isNullOrBlank()) {
            log.info("일회성 상품이 아닌 환불 알림이라 무시합니다: messageId={}, productType={}", messageId, notification.productType)
            return
        }
        val partial =
            when (notification.refundType) {
                REFUND_TYPE_FULL -> {
                    false
                }

                REFUND_TYPE_QUANTITY_BASED_PARTIAL -> {
                    true
                }

                else -> {
                    log.error("알 수 없는 환불 종류라 회수하지 않았습니다. 대사 배치가 다시 봅니다: messageId={}, refundType={}", messageId, notification.refundType)
                    return
                }
            }
        try {
            reclaimer.reclaim(token, partial, "$SOURCE_PREFIX$messageId")
        } catch (e: BusinessException) {
            if (e.errorCode == ErrorCode.PAYMENT_NOT_FOUND) {
                // 지급 전에 환불됐거나 다른 환경의 구매다. 재전송해도 같으므로 ack 하고 대사 배치에 맡긴다.
                log.warn("환불 알림의 결제 기록이 없습니다. 대사 배치가 다시 봅니다: messageId={}, orderId={}", messageId, notification.orderId)
                return
            }
            throw e
        }
    }

    /**
     * 지불 거절 검토 요청. 응답(이의 제기)은 하지 않고 사람이 Play Console 에서 볼 수 있게 주문번호와 사유만 남긴다.
     * 응답에 쓰는 pendingRefundToken 은 남기지 않는다. 여기서 회수하지도 않는다(아직 확정 전이다).
     */
    private fun handlePendingRefundReview(
        messageId: String,
        notification: GoogleDeveloperNotification,
        review: PendingRefundReviewNotification,
    ) {
        log.warn(
            "지불 거절 검토 요청을 받았습니다. 이 서버는 응답하지 않으며, 확정되면 대사 배치가 voided(사유 7)로 회수합니다: " +
                "messageId={}, orderId={}, refundReason={}, eventTimeMillis={}",
            messageId,
            review.orderId,
            review.refundReason,
            notification.eventTimeMillis,
        )
    }

    /** 형식이 틀린 메시지는 다시 받아도 같으므로 ERROR 를 남기고 ack 한다. 처리 기록은 남기지 않는다. */
    private fun decode(
        messageId: String,
        data: String?,
    ): GoogleDeveloperNotification? {
        if (data.isNullOrBlank()) {
            log.error("RTDN 메시지에 data 가 없습니다: messageId={}", messageId)
            return null
        }
        return try {
            objectMapper.readValue(Base64.getDecoder().decode(data), GoogleDeveloperNotification::class.java)
        } catch (e: IllegalArgumentException) {
            log.error("RTDN data 를 base64 로 풀지 못했습니다: messageId={}", messageId)
            null
        } catch (e: JacksonException) {
            log.error("RTDN data 를 읽지 못했습니다: messageId={}, error={}", messageId, e.originalMessage)
            null
        }
    }

    private fun kindOf(notification: GoogleDeveloperNotification): GoogleNotificationKind =
        when {
            notification.oneTimeProductNotification != null -> GoogleNotificationKind.ONE_TIME_PRODUCT
            notification.voidedPurchaseNotification != null -> GoogleNotificationKind.VOIDED_PURCHASE
            notification.pendingRefundReviewNotification != null -> GoogleNotificationKind.PENDING_REFUND_REVIEW
            notification.testNotification != null -> GoogleNotificationKind.TEST
            else -> GoogleNotificationKind.OTHER
        }

    private companion object {
        const val ONE_TIME_PRODUCT_PURCHASED = 1
        const val ONE_TIME_PRODUCT_CANCELED = 2
        const val PRODUCT_TYPE_ONE_TIME = 2
        const val REFUND_TYPE_FULL = 1
        const val REFUND_TYPE_QUANTITY_BASED_PARTIAL = 2

        /** 원장 환불 출처. Pub/Sub messageId 는 숫자 문자열이라 49자 안에 들어간다. */
        const val SOURCE_PREFIX = "rtdn:"
    }
}

enum class GoogleNotificationOutcome {
    /** 처리했거나 처리할 필요가 없다. 204 로 응답한다. */
    ACK,

    /** 일시적인 실패라 다시 받아야 한다. 5xx 로 응답한다. */
    RETRY,
}
