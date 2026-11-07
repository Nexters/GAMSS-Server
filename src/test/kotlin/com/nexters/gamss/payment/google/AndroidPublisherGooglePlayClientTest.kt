package com.nexters.gamss.payment.google

import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.auth.Retryable
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Google 응답 JSON 이 우리 값으로 바뀌는 부분과 오류 번역을 검증한다. 통합 테스트는 이 경계 전체를 가짜로
 * 바꾸므로, 필드 이름이나 중첩 객체 처리가 틀려도 거기서는 드러나지 않는다. HTTP 계층만 가짜로 두고
 * 실제 클라이언트 라이브러리를 통과시킨다.
 */
@ExtendWith(OutputCaptureExtension::class)
class AndroidPublisherGooglePlayClientTest {
    private val requests = mutableListOf<Pair<String, String>>()
    private var respond: () -> LowLevelHttpResponse = { json(200, "{}") }

    private val transport =
        object : MockHttpTransport() {
            override fun buildRequest(
                method: String,
                url: String,
            ): LowLevelHttpRequest {
                requests += method to url
                return object : MockLowLevelHttpRequest(url) {
                    override fun execute(): LowLevelHttpResponse = respond()
                }
            }
        }

    private val client =
        AndroidPublisherGooglePlayClient(
            AndroidPublisher.Builder(transport, GsonFactory.getDefaultInstance(), null).setApplicationName("test").build(),
            PACKAGE_NAME,
        )

    @Test
    fun `v2 구매 조회 응답을 우리 값으로 옮긴다`() {
        respond = {
            json(
                200,
                """
                {
                  "orderId": "GPA.1234-5678-9012-34567",
                  "obfuscatedExternalAccountId": "acc-token",
                  "purchaseCompletionTime": "2026-11-06T01:02:03.456Z",
                  "purchaseStateContext": { "purchaseState": "PURCHASED" },
                  "testPurchaseContext": { "fopType": "TEST" },
                  "productLineItem": [
                    { "productId": "token_300000",
                      "productOfferDetails": { "quantity": 1, "consumptionState": "CONSUMPTION_STATE_YET_TO_BE_CONSUMED" } }
                  ]
                }
                """.trimIndent(),
            )
        }

        val purchase = client.getPurchase("tok")

        assertEquals("GET", requests.single().first)
        assertTrue(requests.single().second.endsWith("/applications/$PACKAGE_NAME/purchases/productsv2/tokens/tok"))
        assertEquals(GooglePurchaseState.PURCHASED, purchase.purchaseState)
        assertEquals("GPA.1234-5678-9012-34567", purchase.orderId)
        assertEquals("acc-token", purchase.obfuscatedExternalAccountId)
        assertEquals(Instant.parse("2026-11-06T01:02:03.456Z"), purchase.purchaseCompletionTime)
        assertTrue(purchase.testPurchase)
        assertEquals(GooglePurchaseLineItem("token_300000", 1, GoogleConsumptionState.YET_TO_BE_CONSUMED), purchase.lineItems.single())
    }

    @Test
    fun `비어 있는 필드는 UNSPECIFIED 와 null 로 채운다`() {
        respond = { json(200, """{ "productLineItem": [ { "productId": "token_300000" } ] }""") }

        val purchase = client.getPurchase("tok")

        assertEquals(GooglePurchaseState.UNSPECIFIED, purchase.purchaseState)
        assertNull(purchase.purchaseCompletionTime)
        assertNull(purchase.obfuscatedExternalAccountId)
        assertFalse(purchase.testPurchase)
        assertEquals(GooglePurchaseLineItem("token_300000", null, GoogleConsumptionState.UNSPECIFIED), purchase.lineItems.single())
    }

    @Test
    fun `PENDING 과 CANCELLED 상태를 읽는다`() {
        respond = { json(200, """{ "purchaseStateContext": { "purchaseState": "PENDING" } }""") }
        assertEquals(GooglePurchaseState.PENDING, client.getPurchase("tok").purchaseState)

        respond = { json(200, """{ "purchaseStateContext": { "purchaseState": "CANCELLED" } }""") }
        assertEquals(GooglePurchaseState.CANCELLED, client.getPurchase("tok").purchaseState)
    }

    @Test
    fun `consume 은 v1 경로로 부른다`() {
        respond = { json(204, "") }

        client.consume("token_300000", "tok")

        assertEquals("POST", requests.single().first)
        assertTrue(requests.single().second.endsWith("/applications/$PACKAGE_NAME/purchases/products/token_300000/tokens/tok:consume"))
    }

    @Test
    fun `잘못된 토큰 응답은 PURCHASE_INVALID 로 바꾼다`() {
        listOf(400, 404, 410).forEach { status ->
            respond = { json(status, ERROR_BODY) }

            assertEquals(ErrorCode.PURCHASE_INVALID, assertFailsWith<BusinessException> { client.getPurchase("tok") }.errorCode)
        }
    }

    @Test
    fun `권한 오류와 일시 장애는 PAYMENT_STORE_UNAVAILABLE 로 바꾼다`() {
        listOf(401, 403, 429, 500, 503).forEach { status ->
            respond = { json(status, ERROR_BODY) }

            assertEquals(
                ErrorCode.PAYMENT_STORE_UNAVAILABLE,
                assertFailsWith<BusinessException> { client.consume("token_300000", "tok") }.errorCode,
            )
        }
    }

    /** 패키지명이 틀린 404 를 잘못된 토큰으로 보면 모든 구매가 경고 없이 거부된다. */
    @Test
    fun `패키지명을 찾지 못한 404 는 설정 오류로 보고 PAYMENT_STORE_UNAVAILABLE 로 바꾼다`() {
        respond = {
            json(404, """{ "error": { "code": 404, "message": "m", "errors": [ { "reason": "applicationNotFound" } ] } }""")
        }

        assertEquals(ErrorCode.PAYMENT_STORE_UNAVAILABLE, assertFailsWith<BusinessException> { client.getPurchase("tok") }.errorCode)
    }

    @Test
    fun `네트워크 오류는 PAYMENT_STORE_UNAVAILABLE 로 바꾼다`() {
        respond = { throw IOException("connection reset") }

        assertEquals(ErrorCode.PAYMENT_STORE_UNAVAILABLE, assertFailsWith<BusinessException> { client.getPurchase("tok") }.errorCode)
    }

    /**
     * 키가 삭제·만료됐거나 서버 시계가 어긋나면 토큰 엔드포인트가 거부한다. 이 예외는 Google API 응답이 아니라
     * 인증 라이브러리의 IOException 으로 올라와, 따로 보지 않으면 타임아웃과 같은 WARN 으로 묻힌다.
     */
    @Test
    fun `액세스 토큰 발급이 거부되면 설정 오류로 ERROR 를 남긴다`(output: CapturedOutput) {
        respond = { throw TokenEndpointRejected() }

        assertEquals(ErrorCode.PAYMENT_STORE_UNAVAILABLE, assertFailsWith<BusinessException> { client.getPurchase("tok") }.errorCode)
        assertTrue(output.out.lines().any { "ERROR" in it && "액세스 토큰을 받지 못했습니다" in it && "invalid_grant" in it })
    }

    /**
     * Google 오류 예외의 메시지에는 요청 URL 이 붙고, URL 경로에 purchaseToken 원문이 들어간다. 예외를 로거에
     * 그대로 넘기면 원문이 로그에 남는다.
     */
    @Test
    fun `오류 경로의 로그에 purchaseToken 원문을 남기지 않는다`(output: CapturedOutput) {
        val responses =
            listOf<() -> LowLevelHttpResponse>(
                { json(404, ERROR_BODY) },
                { json(404, """{ "error": { "code": 404, "message": "m", "errors": [ { "reason": "applicationNotFound" } ] } }""") },
                { json(403, ERROR_BODY) },
                { json(500, ERROR_BODY) },
                { throw IOException("read timed out") },
                { throw TokenEndpointRejected() },
            )

        responses.forEach { response ->
            respond = response
            assertFailsWith<BusinessException> { client.getPurchase(SECRET_TOKEN) }
            assertFailsWith<BusinessException> { client.consume("token_300000", SECRET_TOKEN) }
        }

        assertFalse(SECRET_TOKEN in output.out)
    }

    private fun json(
        status: Int,
        body: String,
    ): LowLevelHttpResponse =
        MockLowLevelHttpResponse()
            .setStatusCode(status)
            .setContentType("application/json; charset=UTF-8")
            .setContent(body)

    /** 인증 라이브러리가 토큰 엔드포인트의 4xx 를 감싸는 예외(package-private)와 같은 모양이다. */
    private class TokenEndpointRejected :
        IOException("Error getting access token for service account: 400 Bad Request {\"error\":\"invalid_grant\"}"),
        Retryable {
        override fun isRetryable(): Boolean = false

        override fun getRetryCount(): Int = 0
    }

    private companion object {
        const val PACKAGE_NAME = "com.nexters.gamss.test"
        const val SECRET_TOKEN = "tok-secret-value-1234"
        const val ERROR_BODY = """{ "error": { "code": 400, "message": "m", "errors": [ { "reason": "invalid" } ] } }"""
    }
}
