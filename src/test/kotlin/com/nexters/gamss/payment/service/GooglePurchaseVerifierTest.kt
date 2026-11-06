package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.domain.PaymentEnvironment
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.google.GoogleConsumptionState
import com.nexters.gamss.payment.google.GooglePurchaseLineItem
import com.nexters.gamss.payment.google.GooglePurchaseState
import com.nexters.gamss.support.FakeGooglePlayClient
import com.nexters.gamss.support.FakeGooglePlayClient.Companion.purchase
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(OutputCaptureExtension::class)
class GooglePurchaseVerifierTest {
    private val client = FakeGooglePlayClient()
    private val verifier = GooglePurchaseVerifier(client)

    @Test
    fun `PURCHASED 구매는 Google 응답 값으로 지급 입력을 만들고 원문 토큰과 해시 거래 키를 함께 담는다`() {
        client.stub(TOKEN, purchase(accountToken = ACCOUNT_TOKEN))

        val result = assertIs<GooglePurchaseVerification.Verified>(verifier.verify(TOKEN, "token_300000"))

        val purchase = result.purchase
        assertEquals(PaymentPlatform.GOOGLE, purchase.platform)
        assertEquals(StoreTransactionKey.ofGooglePurchaseToken(TOKEN), purchase.storeTransactionKey)
        assertEquals(TOKEN, purchase.storePurchaseToken)
        assertEquals(ACCOUNT_TOKEN, purchase.accountToken)
        assertEquals("token_300000", purchase.productId)
        assertEquals(1, purchase.quantity)
        assertEquals(PaymentEnvironment.PRODUCTION, purchase.environment)
        assertEquals(Instant.parse("2026-11-06T00:00:00Z"), purchase.purchasedAt)
        assertEquals("GPA.0000-0000-0000-00000", purchase.storeOrderId)
        assertFalse(result.consumed)
    }

    @Test
    fun `계정 토큰은 판정하지 않고 없는 값도 그대로 넘긴다`() {
        client.stub(TOKEN, purchase(accountToken = null))

        val result = assertIs<GooglePurchaseVerification.Verified>(verifier.verify(TOKEN, "token_300000"))

        assertNull(result.purchase.accountToken)
    }

    @Test
    fun `테스트 구매는 SANDBOX 로 기록한다`() {
        client.stub(TOKEN, purchase(accountToken = ACCOUNT_TOKEN, testPurchase = true))

        val result = assertIs<GooglePurchaseVerification.Verified>(verifier.verify(TOKEN, "token_300000"))

        assertEquals(PaymentEnvironment.SANDBOX, result.purchase.environment)
    }

    @Test
    fun `이미 consume 된 구매는 그 사실을 함께 돌려준다`() {
        client.stub(TOKEN, purchase(accountToken = ACCOUNT_TOKEN, consumption = GoogleConsumptionState.CONSUMED))

        val result = assertIs<GooglePurchaseVerification.Verified>(verifier.verify(TOKEN, "token_300000"))

        assertTrue(result.consumed)
    }

    @Test
    fun `PENDING 구매는 지급하지 않고 대기로 돌려준다`() {
        client.stub(TOKEN, purchase(accountToken = ACCOUNT_TOKEN, state = GooglePurchaseState.PENDING))

        val result = assertIs<GooglePurchaseVerification.Pending>(verifier.verify(TOKEN, "token_300000"))

        assertEquals("token_300000", result.productId)
    }

    @Test
    fun `CANCELLED 구매는 거부한다`() {
        client.stub(TOKEN, purchase(accountToken = ACCOUNT_TOKEN, state = GooglePurchaseState.CANCELLED))

        assertErrorCode(ErrorCode.PURCHASE_CANCELLED)
    }

    @Test
    fun `알 수 없는 상태의 구매는 거부한다`() {
        client.stub(TOKEN, purchase(accountToken = ACCOUNT_TOKEN, state = GooglePurchaseState.UNSPECIFIED))

        assertErrorCode(ErrorCode.PURCHASE_INVALID)
    }

    @Test
    fun `구매한 상품이 요청과 다르면 거부한다`() {
        client.stub(TOKEN, purchase(accountToken = ACCOUNT_TOKEN, productId = "token_1000000"))

        assertErrorCode(ErrorCode.PURCHASE_PRODUCT_MISMATCH)
    }

    /** 앱이 상품 ID 를 잘못 보낸 경우와 구분한다. 무엇을 보내도 같은 결과라 PURCHASE_PRODUCT_MISMATCH 로 안내하면 안 된다. */
    @Test
    fun `상품이 없거나 여러 개면 확인할 수 없는 구매로 거부하고 ERROR 를 남긴다`(output: CapturedOutput) {
        val base = purchase(accountToken = ACCOUNT_TOKEN)
        val item = GooglePurchaseLineItem("token_300000", 1, GoogleConsumptionState.YET_TO_BE_CONSUMED)

        client.stub(TOKEN, base.copy(lineItems = emptyList()))
        assertErrorCode(ErrorCode.PURCHASE_INVALID)

        client.stub(TOKEN, base.copy(lineItems = listOf(item, item)))
        assertErrorCode(ErrorCode.PURCHASE_INVALID)

        client.stub(TOKEN, base.copy(lineItems = listOf(item.copy(productId = null))))
        assertErrorCode(ErrorCode.PURCHASE_INVALID)

        assertTrue(output.out.lines().any { "ERROR" in it && "상품 구성을 알 수 없어" in it })
    }

    @Test
    fun `기대 상품이 없으면(웹훅 경로) 응답의 상품을 그대로 쓴다`() {
        client.stub(TOKEN, purchase(accountToken = ACCOUNT_TOKEN, productId = "token_1000000"))

        val result = assertIs<GooglePurchaseVerification.Verified>(verifier.verify(TOKEN, null))

        assertEquals("token_1000000", result.purchase.productId)
    }

    @Test
    fun `여러 개를 산 구매는 지급하지 않고 수동 처리 대상으로 ERROR 를 남긴다`(output: CapturedOutput) {
        client.stub(TOKEN, purchase(accountToken = ACCOUNT_TOKEN, quantity = 2))

        assertErrorCode(ErrorCode.PURCHASE_QUANTITY_UNSUPPORTED)
        assertTrue(output.out.lines().any { "ERROR" in it && "여러 개를 산 Google 구매" in it })
        assertFalse(output.out.contains(TOKEN))
    }

    @Test
    fun `응답에 수량이 없으면 1 로 처리한다`() {
        client.stub(TOKEN, purchase(accountToken = ACCOUNT_TOKEN, quantity = null))

        val result = assertIs<GooglePurchaseVerification.Verified>(verifier.verify(TOKEN, "token_300000"))

        assertEquals(1, result.purchase.quantity)
    }

    @Test
    fun `PURCHASED 인데 완료 시각이 없으면 지급하지 않는다`() {
        client.stub(TOKEN, purchase(accountToken = ACCOUNT_TOKEN, completedAt = null))

        assertErrorCode(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
    }

    private fun assertErrorCode(expected: ErrorCode) {
        val error = assertFailsWith<BusinessException> { verifier.verify(TOKEN, "token_300000") }
        assertEquals(expected, error.errorCode)
    }

    private companion object {
        const val TOKEN = "purchase-token-original-value"
        const val ACCOUNT_TOKEN = "3f0c6a8e-1b2d-4c5e-8f9a-0b1c2d3e4f50"
    }
}
