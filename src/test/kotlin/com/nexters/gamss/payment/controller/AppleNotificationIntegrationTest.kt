package com.nexters.gamss.payment.controller

import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.payment.domain.PaymentEnvironment
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.repository.AppleNotificationRecordRepository
import com.nexters.gamss.payment.repository.PaymentAccountRepository
import com.nexters.gamss.payment.repository.PaymentJobCheckpointRepository
import com.nexters.gamss.payment.repository.PaymentRepository
import com.nexters.gamss.payment.service.AppleNotificationHistoryReconciler
import com.nexters.gamss.payment.service.AppleNotificationService
import com.nexters.gamss.payment.service.PaymentAccountService
import com.nexters.gamss.support.FakeAppleNotificationConfig
import com.nexters.gamss.support.FakeAppleNotificationDecoder
import com.nexters.gamss.support.FakeAppleNotificationDecoder.Companion.notification
import com.nexters.gamss.support.FakeAppleServerApiClient
import com.nexters.gamss.support.FakeAppleTransactionDecoder
import com.nexters.gamss.support.FakeAppleTransactionDecoderConfig
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.wallet.domain.TokenAmount
import com.nexters.gamss.wallet.domain.TokenLedgerType
import com.nexters.gamss.wallet.domain.TokenWalletChange
import com.nexters.gamss.wallet.repository.TokenLedgerRepository
import com.nexters.gamss.wallet.repository.TokenWalletRepository
import com.nexters.gamss.wallet.service.TokenWalletReader
import com.nexters.gamss.wallet.service.TokenWalletService
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * App Store 서버 알림 웹훅과 알림 기록 배치(#245)를 실제 DB 로 통과시킨다. 서명 검증과 Apple 호출만 가짜다.
 * 지급·회수는 트랜잭션 안에서 부를 수 없으므로 테스트 트랜잭션 롤백 대신 끝나고 직접 정리한다.
 * open-in-view 는 운영과 같이 끈다(이유는 GoogleRefundIntegrationTest 와 같다).
 */
@SpringBootTest(properties = ["spring.jpa.open-in-view=false"])
@Import(TestcontainersConfig::class, FakeAppleTransactionDecoderConfig::class, FakeAppleNotificationConfig::class)
@ExtendWith(OutputCaptureExtension::class)
class AppleNotificationIntegrationTest {
    @Autowired private lateinit var context: WebApplicationContext

    @Autowired private lateinit var transactions: FakeAppleTransactionDecoder

    @Autowired private lateinit var notifications: FakeAppleNotificationDecoder

    @Autowired private lateinit var serverApi: FakeAppleServerApiClient

    @Autowired private lateinit var reconciler: AppleNotificationHistoryReconciler

    @Autowired private lateinit var recordRepository: AppleNotificationRecordRepository

    @Autowired private lateinit var checkpointRepository: PaymentJobCheckpointRepository

    @Autowired private lateinit var memberRepository: MemberRepository

    @Autowired private lateinit var identityRepository: MemberSocialIdentityRepository

    @Autowired private lateinit var accountService: PaymentAccountService

    @Autowired private lateinit var accountRepository: PaymentAccountRepository

    @Autowired private lateinit var paymentRepository: PaymentRepository

    @Autowired private lateinit var walletRepository: TokenWalletRepository

    @Autowired private lateinit var ledgerRepository: TokenLedgerRepository

    @Autowired private lateinit var walletReader: TokenWalletReader

    @Autowired private lateinit var walletService: TokenWalletService

    @Autowired private lateinit var meterRegistry: MeterRegistry

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired private lateinit var jwtIssuer: JwtIssuer

    private lateinit var mockMvc: MockMvc
    private lateinit var buyer: Member

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        transactions.reset()
        notifications.reset()
        serverApi.reset()
        buyer = memberRepository.save(Member("apple-notification-buyer@a.com"))
        identityRepository.upsert(buyer.id, BUYER_SUBJECT)
        stubTransactions()
    }

    @AfterEach
    fun cleanUp() {
        ledgerRepository.deleteAll()
        walletRepository.deleteAll()
        paymentRepository.deleteAll()
        accountRepository.deleteAll()
        identityRepository.deleteAll()
        memberRepository.deleteAllById(listOf(buyer.id))
        jdbcTemplate.update("delete from apple_notification")
        jdbcTemplate.update("delete from apple_refund_state")
        jdbcTemplate.update("delete from payment_job_checkpoint")
    }

    // ---- 서명 ----

    @Test
    fun `서명을 확인할 수 없는 알림은 400 으로 거부하고 카운터를 올린다`() {
        val before = rejectedCount()

        postRaw("""{"signedPayload": "forged"}""").andExpect { status { isBadRequest() } }
        postRaw("""{}""").andExpect { status { isBadRequest() } }

        assertEquals(before + 2, rejectedCount())
    }

    /** Apple 은 POST 만 보낸다. 다른 메서드까지 열어 두면 같은 경로에 API 가 붙는 순간 인증 없이 뚫린다. */
    @Test
    fun `GET 은 인증 없이 열리지 않는다`() {
        mockMvc.get(WEBHOOK).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `알림 서명은 맞는데 거래 서명을 확인할 수 없으면 503 으로 다시 받고 기록하지 않는다`() {
        notify("n-bad-tx", "ONE_TIME_CHARGE", "unknown-transaction-jws").andExpect { status { isServiceUnavailable() } }

        assertFalse(recordRepository.isNotificationHandled("n-bad-tx"))
    }

    // ---- 지급 ----

    @Test
    fun `앱이 지급 요청을 보내지 않아도 ONE_TIME_CHARGE 로 지급한다`() {
        notify("n-charge", "ONE_TIME_CHARGE", PURCHASE_JWS).andExpect { status { isOk() } }

        assertEquals(300_000, payment().grantedTokens)
        assertEquals(300_000, walletReader.balanceOf(buyer.id))
        assertTrue(recordRepository.isNotificationHandled("n-charge"))
    }

    @Test
    fun `앱이 이미 지급한 거래의 ONE_TIME_CHARGE 는 더 지급하지 않는다`() {
        purchaseByApp(PURCHASE_JWS).andExpect { status { isOk() } }

        notify("n-charge-after-app", "ONE_TIME_CHARGE", PURCHASE_JWS).andExpect { status { isOk() } }

        assertEquals(300_000, walletReader.balanceOf(buyer.id))
    }

    /** prod 의 TestFlight 결제 알림이 dev 로 오는 경우다. 실제 돈이 오가지 않아 ERROR 대신 WARN 으로 남긴다. */
    @Test
    fun `다른 환경에서 산 Sandbox 거래는 WARN 을 남기고 200 으로 처리한다`(output: CapturedOutput) {
        transactions.stub(
            "sandbox-other-env-jws",
            FakeAppleTransactionDecoder.transaction(
                accountToken = "11111111-2222-4333-8444-555555555555",
                transactionId = "2000000999000001",
                environment = PaymentEnvironment.SANDBOX,
            ),
        )

        notify("n-other-env", "ONE_TIME_CHARGE", "sandbox-other-env-jws").andExpect { status { isOk() } }

        assertTrue(output.out.lines().any { "WARN" in it && "주인을 모르는 테스트 구매" in it && "2000000999000001" in it })
        assertTrue(recordRepository.isNotificationHandled("n-other-env"))
    }

    // ---- 환불 ----

    @Test
    fun `전액 환불 알림이 오면 회수하고, 이미 썼으면 잔액이 음수가 된다`() {
        notify("n-charge", "ONE_TIME_CHARGE", PURCHASE_JWS)
        spend(200_000)

        notify("n-refund", "REFUND", FULL_REFUND_JWS).andExpect { status { isOk() } }

        assertEquals(300_000, payment().refundedTokens)
        assertEquals(-200_000, walletReader.balanceOf(buyer.id))
    }

    @Test
    fun `부분 환불은 비율만큼만 회수한다`() {
        notify("n-charge", "ONE_TIME_CHARGE", PURCHASE_JWS)

        notify("n-refund-half", "REFUND", HALF_REFUND_JWS).andExpect { status { isOk() } }

        assertEquals(150_000, payment().refundedTokens)
        assertEquals(150_000, walletReader.balanceOf(buyer.id))
    }

    @Test
    fun `환불 취소 알림이 오면 회수한 만큼 돌려준다`() {
        notify("n-charge", "ONE_TIME_CHARGE", PURCHASE_JWS)
        notify("n-refund", "REFUND", FULL_REFUND_JWS)

        notify("n-reversed", "REFUND_REVERSED", REVERSED_JWS).andExpect { status { isOk() } }

        assertEquals(0, payment().refundedTokens)
        assertEquals(300_000, walletReader.balanceOf(buyer.id))
    }

    /** Apple 은 실패한 알림을 몇 시간 뒤에 다시 보내므로 환불 알림이 환불 취소보다 늦게 도착할 수 있다. */
    @Test
    fun `환불 취소 뒤에 옛 환불 알림이 늦게 와도 다시 회수하지 않는다`() {
        notify("n-charge", "ONE_TIME_CHARGE", PURCHASE_JWS)
        notify("n-reversed", "REFUND_REVERSED", REVERSED_JWS)

        notify("n-refund-late", "REFUND", FULL_REFUND_JWS).andExpect { status { isOk() } }

        assertEquals(0, payment().refundedTokens)
        assertEquals(300_000, walletReader.balanceOf(buyer.id))
    }

    @Test
    fun `같은 알림이 다시 와도 한 번만 처리한다`() {
        notify("n-charge", "ONE_TIME_CHARGE", PURCHASE_JWS)
        notify("n-refund", "REFUND", FULL_REFUND_JWS)
        notify("n-reversed", "REFUND_REVERSED", REVERSED_JWS)

        notify("n-refund", "REFUND", FULL_REFUND_JWS).andExpect { status { isOk() } }

        assertEquals(0, payment().refundedTokens)
        assertEquals(300_000, walletReader.balanceOf(buyer.id))
    }

    @Test
    fun `처리하지 않는 알림은 기록만 하고 200 으로 응답한다`() {
        notify("n-declined", "REFUND_DECLINED", PURCHASE_JWS).andExpect { status { isOk() } }

        assertTrue(recordRepository.isNotificationHandled("n-declined"))
        assertNull(paymentOrNull())
    }

    // ---- 지급보다 먼저 온 환불 ----

    /** 구매 직후의 JWS 를 들고 있다가 환불받은 뒤 보내면 JWS 에는 환불 표시가 없다(#254 리뷰). */
    @Test
    fun `환불 알림이 지급보다 먼저 온 거래는 나중에 환불 전 JWS 로 지급 요청이 와도 토큰이 남지 않는다`() {
        notify("n-refund-first", "REFUND", FULL_REFUND_JWS).andExpect { status { isOk() } }
        assertNull(paymentOrNull())

        purchaseByApp(PURCHASE_JWS).andExpect {
            status { isOk() }
            jsonPath("$.data.paidBalance") { value(0) }
        }

        assertEquals(300_000, payment().refundedTokens)
        assertEquals(0, walletReader.balanceOf(buyer.id))
    }

    @Test
    fun `지급보다 먼저 온 환불도 나중에 환불 취소가 오면 돌려준다`() {
        notify("n-refund-first", "REFUND", FULL_REFUND_JWS)
        purchaseByApp(PURCHASE_JWS)

        notify("n-reversed", "REFUND_REVERSED", REVERSED_JWS).andExpect { status { isOk() } }

        assertEquals(0, payment().refundedTokens)
        assertEquals(300_000, walletReader.balanceOf(buyer.id))
    }

    @Test
    fun `환불 알림이 구매 알림보다 먼저 와도 지급 직후 회수한다`() {
        notify("n-refund-first", "REFUND", FULL_REFUND_JWS)

        notify("n-charge", "ONE_TIME_CHARGE", PURCHASE_JWS).andExpect { status { isOk() } }

        assertEquals(300_000, payment().refundedTokens)
        assertEquals(0, walletReader.balanceOf(buyer.id))
    }

    // ---- 알림 기록 배치 ----

    @Test
    fun `알림 기록 배치는 처리 기록에 없는 알림만 처리하고 마지막 성공 시각을 남긴다`() {
        notify("n-charge", "ONE_TIME_CHARGE", PURCHASE_JWS)
        stubNotification("n-refund-missed", "REFUND", FULL_REFUND_JWS)
        serverApi.pages = listOf(listOf(payload("n-charge")), listOf(payload("n-refund-missed")))

        reconciler.reconcile(NOW)

        assertEquals(300_000, payment().refundedTokens)
        assertTrue(recordRepository.isNotificationHandled("n-refund-missed"))
        assertEquals(NOW, checkpointRepository.lastSucceededAt(AppleNotificationHistoryReconciler.JOB_NAME))
        assertEquals(NOW.minus(serverApi.maxLookback), serverApi.requests.first().first)
    }

    @Test
    fun `다음 회차는 마지막 성공 시각보다 하루 앞에서부터 조회한다`() {
        val lastRun = NOW.minusSeconds(86_400)
        checkpointRepository.saveLastSucceededAt(AppleNotificationHistoryReconciler.JOB_NAME, lastRun)

        reconciler.reconcile(NOW)

        assertEquals(lastRun.minus(AppleNotificationHistoryReconciler.OVERLAP), serverApi.requests.single().first)
    }

    @Test
    fun `Apple 과 통신할 수 없으면 회차를 멈추고 마지막 성공 시각을 갱신하지 않는다`() {
        serverApi.unavailable = true

        assertThrows<RuntimeException> { reconciler.reconcile(NOW) }

        assertNull(checkpointRepository.lastSucceededAt(AppleNotificationHistoryReconciler.JOB_NAME))
    }

    @Test
    fun `처리하지 못한 항목이 있으면 나머지는 처리하고 회차를 실패로 끝낸다`() {
        stubNotification("n-charge", "ONE_TIME_CHARGE", PURCHASE_JWS)
        serverApi.pages = listOf(listOf("forged", payload("n-charge")))

        assertThrows<IllegalStateException> { reconciler.reconcile(NOW) }

        assertNotNull(paymentOrNull())
        assertNull(checkpointRepository.lastSucceededAt(AppleNotificationHistoryReconciler.JOB_NAME))
    }

    @Test
    fun `다시 받아야 하는 항목이 있어도 뒤 항목을 처리하고 회차를 실패로 끝낸다`() {
        stubNotification("n-bad-tx", "ONE_TIME_CHARGE", "unknown-transaction-jws")
        stubNotification("n-charge", "ONE_TIME_CHARGE", PURCHASE_JWS)
        serverApi.pages = listOf(listOf(payload("n-bad-tx"), payload("n-charge")))

        assertThrows<IllegalStateException> { reconciler.reconcile(NOW) }

        assertNotNull(paymentOrNull())
        assertFalse(recordRepository.isNotificationHandled("n-bad-tx"))
        assertNull(checkpointRepository.lastSucceededAt(AppleNotificationHistoryReconciler.JOB_NAME))
    }

    // ---- 도우미 ----

    private fun stubTransactions() {
        val purchase = FakeAppleTransactionDecoder.transaction(accountToken = buyerAccountToken(), transactionId = TRANSACTION_ID)
        transactions.stub(PURCHASE_JWS, purchase.copy(signedDate = PURCHASED_AT))
        transactions.stub(FULL_REFUND_JWS, purchase.copy(revocationDate = REFUNDED_AT, signedDate = REFUNDED_AT))
        transactions.stub(
            HALF_REFUND_JWS,
            purchase.copy(revocationDate = REFUNDED_AT, revocationPercentage = 50_000, signedDate = REFUNDED_AT),
        )
        transactions.stub(REVERSED_JWS, purchase.copy(signedDate = REVERSED_AT))
    }

    private fun stubNotification(
        uuid: String,
        type: String,
        signedTransactionInfo: String,
    ) {
        notifications.stub(payload(uuid), notification(uuid, type, signedTransactionInfo))
    }

    private fun notify(
        uuid: String,
        type: String,
        signedTransactionInfo: String,
    ): ResultActionsDsl {
        stubNotification(uuid, type, signedTransactionInfo)
        return postRaw("""{"signedPayload": "${payload(uuid)}"}""")
    }

    private fun postRaw(body: String): ResultActionsDsl =
        mockMvc.post(WEBHOOK) {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun purchaseByApp(signedTransaction: String): ResultActionsDsl =
        mockMvc.post("/api/payments/apple") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${jwtIssuer.issueAccessToken(buyer.id)}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"signedTransaction": "$signedTransaction"}"""
        }

    private fun spend(amount: Long) {
        walletService.apply(
            TokenWalletChange(subjectKey = BUYER_SUBJECT, type = TokenLedgerType.CONSUME, amount = TokenAmount(amount), refId = null),
        )
    }

    private fun payload(uuid: String) = "signed-payload-$uuid"

    private fun buyerAccountToken(): String = accountService.accountTokenOf(buyer.id).value

    private fun paymentOrNull() = paymentRepository.findByPlatformAndStoreTransactionKey(PaymentPlatform.APPLE, TRANSACTION_ID)

    private fun payment() = requireNotNull(paymentOrNull())

    private fun rejectedCount(): Double = meterRegistry.counter(AppleNotificationService.REJECTED_METRIC).count()

    private companion object {
        const val WEBHOOK = "/api/webhooks/apple"
        const val TRANSACTION_ID = "2000000777000001"
        const val PURCHASE_JWS = "apple-purchase-jws"
        const val FULL_REFUND_JWS = "apple-full-refund-jws"
        const val HALF_REFUND_JWS = "apple-half-refund-jws"
        const val REVERSED_JWS = "apple-refund-reversed-jws"
        val BUYER_SUBJECT = "e".repeat(64)
        val NOW: Instant = Instant.parse("2026-11-12T00:00:00Z")
        val PURCHASED_AT: Instant = Instant.parse("2026-11-06T00:00:00Z")
        val REFUNDED_AT: Instant = Instant.parse("2026-11-08T00:00:00Z")
        val REVERSED_AT: Instant = Instant.parse("2026-11-10T00:00:00Z")
    }
}
