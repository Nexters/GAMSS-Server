package com.nexters.gamss.payment.google

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.model.ProductPurchaseV2
import com.google.auth.Retryable
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Google Play Developer API 를 실제로 부르는 구현.
 *
 * 로그와 예외 메시지에 purchaseToken 원문과 Google 응답 본문을 넣지 않는다. 원문은 그 자체로 구매를
 * 조회하고 consume 할 수 있는 값이다.
 */
class AndroidPublisherGooglePlayClient(
    private val publisher: AndroidPublisher,
    private val packageName: String,
) : GooglePlayClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override val configured: Boolean = true

    override fun getPurchase(purchaseToken: String): GooglePlayPurchase =
        call("구매 조회") {
            publisher
                .purchases()
                .productsv2()
                .getproductpurchasev2(packageName, purchaseToken)
                .execute()
        }.let { toPurchase(it) }

    override fun consume(
        productId: String,
        purchaseToken: String,
    ) {
        call("consume") {
            publisher
                .purchases()
                .products()
                .consume(packageName, productId, purchaseToken)
                .execute()
        }
    }

    private fun toPurchase(response: ProductPurchaseV2): GooglePlayPurchase =
        GooglePlayPurchase(
            purchaseState = GooglePurchaseState.of(response.purchaseStateContext?.purchaseState),
            orderId = response.orderId,
            obfuscatedExternalAccountId = response.obfuscatedExternalAccountId,
            purchaseCompletionTime = response.purchaseCompletionTime?.let { parseTime(it) },
            testPurchase = response.testPurchaseContext != null,
            lineItems =
                response.productLineItem.orEmpty().map { item ->
                    GooglePurchaseLineItem(
                        productId = item.productId,
                        quantity = item.productOfferDetails?.quantity,
                        consumptionState = GoogleConsumptionState.of(item.productOfferDetails?.consumptionState),
                    )
                },
        )

    private fun parseTime(value: String) =
        try {
            OffsetDateTime.parse(value).toInstant()
        } catch (e: DateTimeParseException) {
            log.error("Google 구매 응답의 시각 형식을 읽지 못했습니다: value={}", value, e)
            throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
        }

    /**
     * Google 오류를 우리 에러 코드로 바꾼다.
     *
     * - 401·403, 404 applicationNotFound: 서비스 계정 권한, API 설정, 패키지명 문제다. 모든 구매가 막히는 상태라
     *   ERROR 로 남기고 503 으로 응답한다. 설정을 고친 뒤 앱이 다시 보내면 지급된다.
     * - 그 밖의 400·404·410: 토큰이 잘못됐거나 다른 앱의 것이다. 요청이 틀린 것이라 WARN 이다.
     * - 그 밖(429·5xx·네트워크·타임아웃): 일시적인 장애로 보고 WARN 으로 남긴다. 앱이 다시 보낸다.
     * - 액세스 토큰 발급 거부: 키 삭제·만료·비활성이나 서버 시계 어긋남(invalid_grant)이다. 다시 보내도
     *   같은 결과라 설정 문제와 같이 ERROR 로 남긴다. 기동할 때는 토큰을 받지 않으므로 이 상태로도 서버가
     *   뜨고, 지급 전에 실패해 결제 기록이 없으니 consume 적체 경보에도 잡히지 않는다.
     */
    private fun <T> call(
        operation: String,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (e: GoogleJsonResponseException) {
            throw translate(operation, e.statusCode, e)
        } catch (e: IOException) {
            if (e is Retryable && !e.isRetryable) {
                // 토큰 엔드포인트의 4xx 다. 메시지에는 토큰 엔드포인트 응답과 서비스 계정 이메일만 들어 있다
                // (개인 키·purchaseToken 없음). 사유가 있어야 타임아웃과 구분된다.
                log.error(
                    "Google Play {} 호출에 쓸 액세스 토큰을 받지 못했습니다. 서비스 계정 키와 서버 시계를 확인하세요: {}",
                    operation,
                    e.message,
                )
            } else {
                log.warn("Google Play {} 호출이 실패했습니다: {}", operation, e.javaClass.simpleName)
            }
            throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
        }

    private fun translate(
        operation: String,
        status: Int,
        e: GoogleJsonResponseException,
    ): BusinessException {
        val reason =
            e.details
                ?.errors
                ?.firstOrNull()
                ?.reason
        return when {
            reason == APPLICATION_NOT_FOUND || status == 401 || status == 403 -> {
                log.error(
                    "Google Play {} 호출이 설정 문제로 거부됐습니다. 패키지명, 서비스 계정 권한, API 사용 설정을 확인하세요: " +
                        "status={}, reason={}",
                    operation,
                    status,
                    reason,
                )
                BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
            }

            status == 400 || status == 404 || status == 410 -> {
                log.warn("Google Play 가 {} 요청을 거부했습니다: status={}, reason={}", operation, status, reason)
                BusinessException(ErrorCode.PURCHASE_INVALID)
            }

            else -> {
                log.warn("Google Play {} 호출이 실패했습니다: status={}", operation, status)
                BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
            }
        }
    }

    private companion object {
        /**
         * 패키지명이 틀렸을 때 Google 이 404 와 함께 주는 사유. 토큰이 틀린 404 와 구분하지 않으면 모든 Android
         * 구매가 '확인할 수 없는 구매'로 WARN 만 남기고 거부되고, 결제 기록이 없어 적체 경보에도 잡히지 않는다.
         */
        const val APPLICATION_NOT_FOUND = "applicationNotFound"
    }
}
