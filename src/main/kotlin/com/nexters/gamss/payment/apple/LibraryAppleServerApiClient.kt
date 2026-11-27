package com.nexters.gamss.payment.apple

import com.apple.itunes.storekit.client.APIException
import com.apple.itunes.storekit.client.AppStoreServerAPIClient
import com.apple.itunes.storekit.model.Environment
import com.apple.itunes.storekit.model.NotificationHistoryRequest
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** `app-store-server-library` 의 [AppStoreServerAPIClient] 로 App Store Server API 를 부른다. */
class LibraryAppleServerApiClient(
    private val client: AppStoreServerAPIClient,
    environment: Environment,
) : AppleServerApiClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override val configured = true

    // 경계에서 거부되지 않게 한 시간 줄인다.
    override val maxLookback: Duration =
        Duration.ofDays(if (environment == Environment.SANDBOX) SANDBOX_HISTORY_DAYS else PRODUCTION_HISTORY_DAYS).minusHours(1)

    override fun notificationHistory(
        start: Instant,
        end: Instant,
        paginationToken: String?,
    ): AppleNotificationHistoryPage {
        val request = NotificationHistoryRequest().startDate(start.toEpochMilli()).endDate(end.toEpochMilli())
        val response =
            try {
                client.getNotificationHistory(paginationToken, request)
            } catch (e: APIException) {
                log.warn("App Store 알림 기록을 조회하지 못했습니다: status={}, apiError={}", e.httpStatusCode, e.rawApiError)
                throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
            } catch (e: IOException) {
                log.warn("App Store Server API 와 통신할 수 없습니다", e)
                throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
            }
        return AppleNotificationHistoryPage(
            signedPayloads = response.notificationHistory.orEmpty().mapNotNull { it.signedPayload },
            nextPaginationToken = if (response.hasMore == true) response.paginationToken else null,
        )
    }

    private companion object {
        const val PRODUCTION_HISTORY_DAYS = 180L
        const val SANDBOX_HISTORY_DAYS = 30L
    }
}

/** In-App Purchase 키가 없는 환경. 알림 기록 배치가 돌지 않는다. */
class UnconfiguredAppleServerApiClient : AppleServerApiClient {
    override val configured = false

    override val maxLookback: Duration = Duration.ZERO

    override fun notificationHistory(
        start: Instant,
        end: Instant,
        paginationToken: String?,
    ): AppleNotificationHistoryPage = throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
}
