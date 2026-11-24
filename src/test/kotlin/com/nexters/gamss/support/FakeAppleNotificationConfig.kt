package com.nexters.gamss.support

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.apple.AppleNotification
import com.nexters.gamss.payment.apple.AppleNotificationDecoder
import com.nexters.gamss.payment.apple.AppleNotificationHistoryPage
import com.nexters.gamss.payment.apple.AppleServerApiClient
import com.nexters.gamss.payment.apple.InvalidAppleNotificationException
import com.nexters.gamss.payment.domain.PaymentEnvironment
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Duration
import java.time.Instant

/**
 * 통합 테스트에서 App Store 알림 서명 검증과 Server API 대신 쓰는 가짜 구현.
 * 실제 서명 검증은 `SignedAppleNotificationDecoderTest` 가 맡는다.
 */
@TestConfiguration(proxyBeanMethods = false)
class FakeAppleNotificationConfig {
    @Bean
    @Primary
    fun fakeAppleNotificationDecoder(): FakeAppleNotificationDecoder = FakeAppleNotificationDecoder()

    @Bean
    @Primary
    fun fakeAppleServerApiClient(): FakeAppleServerApiClient = FakeAppleServerApiClient()
}

/** `signedPayload` 마다 풀린 알림을 심어 두고, 심지 않은 문자열은 서명 검증 실패로 거부한다. */
class FakeAppleNotificationDecoder : AppleNotificationDecoder {
    private val notifications = mutableMapOf<String, AppleNotification>()

    fun stub(
        signedPayload: String,
        notification: AppleNotification,
    ) {
        notifications[signedPayload] = notification
    }

    fun reset() {
        notifications.clear()
    }

    override fun decode(signedPayload: String): AppleNotification =
        notifications[signedPayload] ?: throw InvalidAppleNotificationException("심지 않은 알림입니다.")

    companion object {
        fun notification(
            uuid: String,
            type: String,
            signedTransactionInfo: String?,
            subtype: String? = null,
            signedDate: Instant = Instant.parse("2026-11-12T00:00:00Z"),
            environment: PaymentEnvironment = PaymentEnvironment.PRODUCTION,
        ) = AppleNotification(
            notificationUuid = uuid,
            type = type,
            subtype = subtype,
            signedDate = signedDate,
            environment = environment,
            signedTransactionInfo = signedTransactionInfo,
        )
    }
}

class FakeAppleServerApiClient : AppleServerApiClient {
    override var configured = true

    override val maxLookback: Duration = Duration.ofDays(180).minusHours(1)

    /** 페이지마다 돌려줄 `signedPayload`. */
    var pages: List<List<String>> = emptyList()

    var unavailable = false

    val requests = mutableListOf<Pair<Instant, Instant>>()

    fun reset() {
        configured = true
        pages = emptyList()
        unavailable = false
        requests.clear()
    }

    override fun notificationHistory(
        start: Instant,
        end: Instant,
        paginationToken: String?,
    ): AppleNotificationHistoryPage {
        if (unavailable) {
            throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
        }
        requests += start to end
        val index = paginationToken?.toInt() ?: 0
        val next = if (index + 1 < pages.size) (index + 1).toString() else null
        return AppleNotificationHistoryPage(pages.getOrElse(index) { emptyList() }, next)
    }
}
