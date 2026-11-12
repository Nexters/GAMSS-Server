package com.nexters.gamss.payment.controller

import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.payment.domain.PaymentAccountToken
import com.nexters.gamss.payment.domain.PaymentEnvironment
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.domain.VerifiedPurchase
import com.nexters.gamss.payment.google.GooglePurchaseState
import com.nexters.gamss.payment.google.GooglePushAuthResult
import com.nexters.gamss.payment.google.GooglePushAuthenticator
import com.nexters.gamss.payment.google.GoogleVoidedPurchase
import com.nexters.gamss.payment.repository.GoogleRefundRecordRepository
import com.nexters.gamss.payment.repository.PaymentAccountRepository
import com.nexters.gamss.payment.repository.PaymentRepository
import com.nexters.gamss.payment.service.GoogleVoidedPurchaseReconciler
import com.nexters.gamss.payment.service.PaymentAccountService
import com.nexters.gamss.payment.service.PurchaseGrantService
import com.nexters.gamss.support.FakeGooglePlayClient
import com.nexters.gamss.support.FakeGooglePlayClient.Companion.purchase
import com.nexters.gamss.support.FakeGooglePlayClientConfig
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
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
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
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Google RTDN 웹훅과 Voided Purchases 대사 배치(#246)를 실제 DB 로 통과시킨다. Google 호출과 push 토큰 검증만 가짜다.
 * 지급·회수는 트랜잭션 안에서 부를 수 없으므로 테스트 트랜잭션 롤백 대신 끝나고 직접 정리한다.
 *
 * 캡처한 출력에는 이 테스트가 남긴 것 말고도 다른 출력(컨텍스트 기동 로그 등)이 섞일 수 있다. 그래서 로그를 확인할 때는
 * 테스트마다 고유한 orderId·messageId 로 줄을 고른다.
 */
@SpringBootTest(properties = ["google.play.package-name=$PACKAGE"])
@Import(TestcontainersConfig::class, FakeGooglePlayClientConfig::class, GoogleRefundIntegrationTest.FakeAuthConfig::class)
@ExtendWith(OutputCaptureExtension::class)
class GoogleRefundIntegrationTest {
    /** 실제 OIDC 검증은 [com.nexters.gamss.payment.google.OidcGooglePushAuthenticatorTest] 가 맡는다. */
    @TestConfiguration(proxyBeanMethods = false)
    class FakeAuthConfig {
        @Bean
        @Primary
        fun fakePushAuthenticator(): GooglePushAuthenticator =
            GooglePushAuthenticator { header ->
                when (header) {
                    null -> GooglePushAuthResult.MISSING
                    "Bearer $VALID_PUSH_TOKEN" -> GooglePushAuthResult.AUTHENTICATED
                    "Bearer $UNAVAILABLE_PUSH_TOKEN" -> GooglePushAuthResult.UNAVAILABLE
                    else -> GooglePushAuthResult.REJECTED
                }
            }
    }

    @Autowired private lateinit var context: WebApplicationContext

    @Autowired private lateinit var google: FakeGooglePlayClient

    @Autowired private lateinit var reconciler: GoogleVoidedPurchaseReconciler

    @Autowired private lateinit var recordRepository: GoogleRefundRecordRepository

    @Autowired private lateinit var grantService: PurchaseGrantService

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

    private lateinit var mockMvc: MockMvc
    private lateinit var buyer: Member

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        google.reset()
        buyer = memberRepository.save(Member("google-refund-buyer@a.com"))
        identityRepository.upsert(buyer.id, BUYER_SUBJECT)
    }

    @AfterEach
    fun cleanUp() {
        ledgerRepository.deleteAll()
        walletRepository.deleteAll()
        paymentRepository.deleteAll()
        accountRepository.deleteAll()
        identityRepository.deleteAll()
        memberRepository.deleteAllById(listOf(buyer.id))
        jdbcTemplate.update("delete from google_notification")
        jdbcTemplate.update("delete from google_voided_purchase")
        jdbcTemplate.update("delete from payment_job_checkpoint")
    }

    // ---- 인증 ----

    @Test
    fun `OIDC 토큰이 없으면 401, 틀리면 403 으로 거부하고 처리하지 않는다`() {
        push("m-auth", testNotification(), authorization = null).andExpect { status { isUnauthorized() } }
        push("m-auth", testNotification(), authorization = "Bearer forged").andExpect { status { isForbidden() } }

        assertFalse(recordRepository.isNotificationHandled("m-auth"))
    }

    @Test
    fun `Google 공개키를 가져오지 못하면 503 으로 다시 받고 처리하지 않는다`() {
        push(
            "m-certs",
            testNotification(),
            authorization = "Bearer $UNAVAILABLE_PUSH_TOKEN",
        ).andExpect { status { isServiceUnavailable() } }

        assertFalse(recordRepository.isNotificationHandled("m-certs"))
    }

    /** Pub/Sub push 는 POST 만 보낸다. 다른 메서드까지 열어 두면 같은 경로에 API 가 붙는 순간 인증 없이 뚫린다. */
    @Test
    fun `GET 은 인증 없이 열리지 않는다`() {
        mockMvc.get(WEBHOOK).andExpect { status { isUnauthorized() } }
    }

    // ---- 알림 종류별 처리 ----

    @Test
    fun `테스트 알림은 204 로 처리한다`() {
        push("m-test", testNotification()).andExpect { status { isNoContent() } }

        assertTrue(recordRepository.isNotificationHandled("m-test"))
    }

    @Test
    fun `다른 앱의 알림은 무시하고 204 로 응답한다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken()))

        push("m-other-app", purchasedNotification(packageName = "com.other.app")).andExpect { status { isNoContent() } }

        assertEquals(0, google.getPurchaseCalls)
        assertNull(paymentOrNull(TOKEN))
    }

    @Test
    fun `앱이 지급 요청을 보내지 않은 구매를 RTDN 으로 지급하고 consume 한다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken()))

        push("m-purchased", purchasedNotification()).andExpect { status { isNoContent() } }

        val payment = assertNotNull(paymentOrNull(TOKEN))
        assertNotNull(payment.consumedAt)
        assertEquals(listOf("token_300000" to TOKEN), google.consumeCalls)
        assertEquals(300_000, walletReader.balanceOf(buyer.id))
    }

    @Test
    fun `같은 메시지가 다시 와도 한 번만 처리한다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken()))

        push("m-dup", purchasedNotification()).andExpect { status { isNoContent() } }
        push("m-dup", purchasedNotification()).andExpect { status { isNoContent() } }

        assertEquals(1, google.getPurchaseCalls)
        assertEquals(300_000, walletReader.balanceOf(buyer.id))
    }

    @Test
    fun `계정 토큰이 없거나 발급한 적 없는 값인 구매는 지급하지 않고 ERROR 로 남긴 뒤 ack 한다`(output: CapturedOutput) {
        val cases =
            listOf(
                Triple("m-unmatched-1", null, "GPA.UNMATCHED-0001"),
                Triple("m-unmatched-2", PaymentAccountToken.of(UUID.randomUUID()).value, "GPA.UNMATCHED-0002"),
            )

        cases.forEachIndexed { index, (messageId, accountToken, orderId) ->
            google.stub(TOKEN, purchase(accountToken = accountToken, orderId = orderId))

            push(messageId, purchasedNotification()).andExpect { status { isNoContent() } }

            assertEquals(index + 1, google.getPurchaseCalls)
            assertTrue(recordRepository.isNotificationHandled(messageId))
            assertTrue(output.out.lines().any { "ERROR" in it && "계정 토큰으로 주인을 찾지 못해" in it && orderId in it })
        }
        assertNull(paymentOrNull(TOKEN))
    }

    /** 계정 토큰이 없거나 형식이 틀린 테스트 구매는 다른 환경의 구매가 아니라 앱 버그라 ERROR 로 남긴다. */
    @Test
    fun `계정 토큰이 없거나 형식이 틀린 테스트 구매는 다른 환경으로 보지 않고 ERROR 로 남긴다`(output: CapturedOutput) {
        val cases =
            listOf(
                Triple("m-sandbox-null", null, "GPA.SANDBOX-NULL-0001"),
                Triple("m-sandbox-malformed", "not-a-uuid", "GPA.SANDBOX-MALFORMED-0001"),
            )

        cases.forEach { (messageId, accountToken, orderId) ->
            google.stub(TOKEN, purchase(accountToken = accountToken, testPurchase = true, orderId = orderId))

            push(messageId, purchasedNotification()).andExpect { status { isNoContent() } }

            assertTrue(output.out.lines().any { "ERROR" in it && "계정 토큰으로 주인을 찾지 못해" in it && orderId in it }, orderId)
            assertFalse(output.out.lines().any { "주인을 모르는 테스트 구매" in it && orderId in it }, orderId)
        }
        assertNull(paymentOrNull(TOKEN))
    }

    /** RTDN 토픽은 패키지당 하나라 prod 구독도 dev 빌드에서 한 라이선스 테스터 구매를 받는다. */
    @Test
    fun `다른 환경 계정 토큰의 테스트 구매는 WARN 으로 남기고 ack 한다`(output: CapturedOutput) {
        val orderId = "GPA.OTHER-ENV-0001"
        google.stub(TOKEN, purchase(accountToken = PaymentAccountToken.of(UUID.randomUUID()).value, testPurchase = true, orderId = orderId))

        push("m-other-env", purchasedNotification()).andExpect { status { isNoContent() } }

        assertNull(paymentOrNull(TOKEN))
        assertTrue(recordRepository.isNotificationHandled("m-other-env"))
        assertTrue(output.out.lines().any { "WARN" in it && "주인을 모르는 테스트 구매" in it && orderId in it })
        assertFalse(output.out.lines().any { "ERROR" in it && orderId in it })
    }

    @Test
    fun `Google 과 통신할 수 없으면 503 으로 다시 받고, 다시 오면 처리한다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken()))
        google.getPurchaseUnavailable = true

        push("m-retry", purchasedNotification()).andExpect { status { isServiceUnavailable() } }
        assertFalse(recordRepository.isNotificationHandled("m-retry"))

        google.getPurchaseUnavailable = false
        push("m-retry", purchasedNotification()).andExpect { status { isNoContent() } }
        assertEquals(300_000, walletReader.balanceOf(buyer.id))
    }

    /** 형식이 깨진 메시지는 다시 받아도 같으므로 ack 하되, 처리했다고 기록하지는 않는다. */
    @Test
    fun `형식이 깨진 메시지는 204 로 ack 하고 기록하지 않는다`() {
        val bodies =
            mapOf(
                "m-no-data" to """{"message": {"messageId": "m-no-data"}}""",
                "m-not-base64" to """{"message": {"data": "not-base64!", "messageId": "m-not-base64"}}""",
                "m-not-json" to """{"message": {"data": "${base64("{")}", "messageId": "m-not-json"}}""",
            )

        bodies.forEach { (messageId, body) ->
            pushRaw(body).andExpect { status { isNoContent() } }
            assertFalse(recordRepository.isNotificationHandled(messageId), messageId)
        }
        pushRaw("""{"message": {"data": "${base64(testNotification())}"}}""").andExpect { status { isNoContent() } }
    }

    @Test
    fun `구매 알림이 왔지만 취소됐거나 결제 대기 중이면 지급하지 않고 기록한다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken(), state = GooglePurchaseState.CANCELLED))
        push("m-cancelled", purchasedNotification()).andExpect { status { isNoContent() } }

        google.stub(TOKEN, purchase(accountToken = buyerAccountToken(), state = GooglePurchaseState.PENDING))
        push("m-pending", purchasedNotification()).andExpect { status { isNoContent() } }

        assertTrue(recordRepository.isNotificationHandled("m-cancelled"))
        assertTrue(recordRepository.isNotificationHandled("m-pending"))
        assertNull(paymentOrNull(TOKEN))
    }

    @Test
    fun `일회성 상품이 아니거나 모르는 환불 종류인 환불 알림은 회수하지 않고 기록한다`() {
        grant()
        val callsAfterGrant = google.getPurchaseCalls

        push("m-subscription", voidedNotification(refundType = 1, productType = 1)).andExpect { status { isNoContent() } }
        push("m-unknown-refund", voidedNotification(refundType = 99)).andExpect { status { isNoContent() } }

        assertTrue(recordRepository.isNotificationHandled("m-subscription"))
        assertTrue(recordRepository.isNotificationHandled("m-unknown-refund"))
        assertEquals(0, payment(TOKEN).refundedTokens)
        assertEquals(callsAfterGrant, google.getPurchaseCalls)
    }

    @Test
    fun `지불 거절 검토 요청은 종류를 따로 기록하고 응답 토큰은 남기지 않는다`(output: CapturedOutput) {
        val orderId = "GPA.CHARGEBACK-0001"

        push("m-review", pendingRefundReviewNotification(orderId)).andExpect { status { isNoContent() } }

        assertEquals(
            "PENDING_REFUND_REVIEW",
            jdbcTemplate.queryForObject(
                "select notification_kind from google_notification where message_id = ?",
                String::class.java,
                "m-review",
            ),
        )
        assertTrue(output.out.lines().any { "WARN" in it && "지불 거절 검토 요청" in it && orderId in it })
        assertFalse(REVIEW_TOKEN in output.out)
    }

    // ---- 웹훅 환불 회수 ----

    @Test
    fun `전액 환불 알림이 오면 회수하고, 이미 썼으면 잔액이 음수가 된다`() {
        grant()
        spend(200_000)

        push("m-voided", voidedNotification(refundType = 1)).andExpect { status { isNoContent() } }

        assertEquals(300_000, payment(TOKEN).refundedTokens)
        assertEquals(-200_000, walletReader.balanceOf(buyer.id))
    }

    /** 웹훅이 남긴 messageId 기록이 배치를 막지 않아야, 지급보다 먼저 온 환불도 나중에 회수된다. */
    @Test
    fun `결제 기록이 없는 환불 알림은 ack 하고 대사 배치에 맡긴다`(output: CapturedOutput) {
        push("m-voided-missing", voidedNotification(refundType = 1)).andExpect { status { isNoContent() } }

        assertTrue(output.out.lines().any { "WARN" in it && "환불 알림의 결제 기록이 없습니다" in it && "m-voided-missing" in it })
        assertTrue(recordRepository.isNotificationHandled("m-voided-missing"))
        assertEquals(0, ledgerRepository.count())

        grant()
        google.voidedPages = listOf(listOf(voided()))
        reconciler.reconcile(NOW)

        assertEquals(300_000, payment(TOKEN).refundedTokens)
    }

    // ---- 부분 환불(다중 수량) ----

    /**
     * 검증기가 수량 1 만 지급하므로 운영에서는 아직 돌지 않는 경로다. 10개 구매가 2, 3, 5개로 나눠 환불되면 목표는 누적
     * 환불 수량을 따라가야 한다. 이번 환불 수량(voidedQuantity)을 목표로 쓰면 덜 회수된다.
     */
    @Test
    fun `부분 환불은 웹훅과 배치 모두 지금까지 환불된 총 수량으로 회수한다`() {
        seedMultiQuantityPayment()
        google.stub(TOKEN_MULTI, purchase(accountToken = buyerAccountToken(), quantity = 10, refundableQuantity = 8))

        push("m-partial", voidedNotification(refundType = 2, token = TOKEN_MULTI)).andExpect { status { isNoContent() } }
        assertEquals(600_000, payment(TOKEN_MULTI).refundedTokens)

        val first = voided(token = TOKEN_MULTI, orderId = "GPA.MULTI-0001", voidedTime = NOW.minus(Duration.ofHours(3)), quantity = 2)
        google.voidedPages = listOf(listOf(first))
        reconciler.reconcile(NOW)
        assertEquals(600_000, payment(TOKEN_MULTI).refundedTokens)

        google.stub(TOKEN_MULTI, purchase(accountToken = buyerAccountToken(), quantity = 10, refundableQuantity = 5))
        val second = voided(token = TOKEN_MULTI, orderId = "GPA.MULTI-0001", voidedTime = NOW.minus(Duration.ofHours(2)), quantity = 3)
        google.voidedPages = listOf(listOf(first, second))
        val callsBefore = google.getPurchaseCalls
        reconciler.reconcile(NOW.plus(Duration.ofHours(1)))
        assertEquals(1_500_000, payment(TOKEN_MULTI).refundedTokens)
        assertEquals(callsBefore + 1, google.getPurchaseCalls)

        val last = voided(token = TOKEN_MULTI, orderId = "GPA.MULTI-0001", voidedTime = NOW.minus(Duration.ofHours(1)))
        google.voidedPages = listOf(listOf(first, second, last))
        reconciler.reconcile(NOW.plus(Duration.ofHours(2)))
        assertEquals(3_000_000, payment(TOKEN_MULTI).refundedTokens)
    }

    // ---- 대사 배치 ----

    @Test
    fun `대사 배치가 웹훅으로 놓친 환불을 회수한다`() {
        grant()
        google.voidedPages = listOf(listOf(voided()))

        reconciler.reconcile(NOW)

        assertEquals(300_000, payment(TOKEN).refundedTokens)
        assertEquals(0, walletReader.balanceOf(buyer.id))
        assertEquals(NOW, recordRepository.lastSucceededAt(GoogleVoidedPurchaseReconciler.JOB_NAME))
        assertEquals(NOW.epochSecond.toDouble(), lastSuccessGauge())
    }

    @Test
    fun `같은 환불이 웹훅과 배치 양쪽에서 와도 한 번만 회수한다`(output: CapturedOutput) {
        grant()
        push("m-voided-both", voidedNotification(refundType = 1)).andExpect { status { isNoContent() } }
        google.voidedPages = listOf(listOf(voided()))

        reconciler.reconcile(NOW)
        assertTrue(recordRepository.isVoidedPurchaseHandled(KEY.value, "voided:${voided().voidedTime.toEpochMilli()}"))
        reconciler.reconcile(NOW.plusSeconds(60))

        assertEquals(300_000, payment(TOKEN).refundedTokens)
        assertEquals(0, walletReader.balanceOf(buyer.id))
        assertEquals(1, ledgerRepository.findAll().count { it.type == TokenLedgerType.REFUND })
        assertTrue(output.out.lines().any { "to=${NOW.plusSeconds(60)}" in it && "처리=0건" in it })
    }

    @Test
    fun `여러 페이지를 끝까지 조회한다`() {
        grant()
        google.voidedPages = listOf(emptyList(), listOf(voided()))

        reconciler.reconcile(NOW)

        assertEquals(listOf(null, "1"), google.listVoidedCalls.map { it.third })
        assertEquals(300_000, payment(TOKEN).refundedTokens)
    }

    @Test
    fun `미승인 구매로 자동 환불된 결제는 전액 회수하고 ERROR 로 남긴다`(output: CapturedOutput) {
        val orderId = "GPA.UNACK-0001"
        grant()
        google.voidedPages = listOf(listOf(voided(orderId = orderId, reason = 8)))

        reconciler.reconcile(NOW)

        assertEquals(300_000, payment(TOKEN).refundedTokens)
        assertEquals(1, output.out.lines().count { "ERROR" in it && "자동 환불" in it && orderId in it })
    }

    /** 결제가 없는 미승인 환불은 지급하지 않은 구매(지급 거부, 다른 환경)라 consume 재시도와 관계가 없다. */
    @Test
    fun `결제가 없는 미승인 자동 환불은 ERROR 없이 WARN 으로 남기고 다음 회차에 다시 본다`(output: CapturedOutput) {
        val orderId = "GPA.NEVER-GRANTED"
        val item = voided(orderId = orderId, reason = 8)
        google.voidedPages = listOf(listOf(item))

        reconciler.reconcile(NOW)

        assertFalse(output.out.lines().any { "ERROR" in it && orderId in it })
        assertTrue(output.out.lines().any { "WARN" in it && "지급한 기록이 없는 구매" in it && orderId in it })
        assertFalse(recordRepository.isVoidedPurchaseHandled(KEY.value, "voided:${item.voidedTime.toEpochMilli()}"))

        grant()
        reconciler.reconcile(NOW.plus(Duration.ofHours(12)))

        assertEquals(300_000, payment(TOKEN).refundedTokens)
        assertTrue(output.out.lines().any { "ERROR" in it && "자동 환불" in it && orderId in it })
    }

    @Test
    fun `지불 거절로 회수한 환불은 사유를 기록한다`(output: CapturedOutput) {
        val orderId = "GPA.CHARGEBACK-VOIDED"
        grant()
        val item = voided(orderId = orderId, reason = 7)
        google.voidedPages = listOf(listOf(item))

        reconciler.reconcile(NOW)

        assertEquals(300_000, payment(TOKEN).refundedTokens)
        assertEquals(
            7,
            jdbcTemplate.queryForObject(
                "select voided_reason from google_voided_purchase where source_id = ?",
                Int::class.java,
                "voided:${item.voidedTime.toEpochMilli()}",
            ),
        )
        assertTrue(output.out.lines().any { "WARN" in it && "voidedReason=7" in it && orderId in it })
    }

    @Test
    fun `결제 기록이 없던 환불은 다음 회차에 다시 보고, 그사이 지급된 결제를 회수한다`() {
        google.voidedPages = listOf(listOf(voided()))
        reconciler.reconcile(NOW)

        grant()
        reconciler.reconcile(NOW.plus(Duration.ofHours(12)))

        assertEquals(300_000, payment(TOKEN).refundedTokens)
        assertEquals(0, walletReader.balanceOf(buyer.id))
        // 겹쳐 조회하는 구간(OVERLAP)이 없으면 둘째 회차가 이 항목의 시각보다 뒤부터 조회해 실제 Google 은 돌려주지 않는다.
        assertFalse(
            google.listVoidedCalls
                .last()
                .first
                .isAfter(voided().voidedTime),
        )
    }

    /** 항목 하나의 예상하지 못한 오류가 같은 회차의 다른 환불을 막지 않는다. 실패한 항목은 다음 회차가 다시 본다. */
    @Test
    fun `항목 하나가 실패해도 나머지를 처리하고, 체크포인트는 갱신하지 않는다`() {
        grant(token = TOKEN, messageId = "m-grant-1")
        grant(token = TOKEN_2, messageId = "m-grant-2")
        google.afterGetPurchase = { throw IllegalStateException("일시 오류") }
        // 부분 환불 항목만 getPurchase 를 부른다.
        google.voidedPages = listOf(listOf(voided(token = TOKEN, quantity = 1), voided(token = TOKEN_2)))

        assertFailsWith<IllegalStateException> { reconciler.reconcile(NOW) }

        assertEquals(0, payment(TOKEN).refundedTokens)
        assertEquals(300_000, payment(TOKEN_2).refundedTokens)
        assertNull(recordRepository.lastSucceededAt(GoogleVoidedPurchaseReconciler.JOB_NAME))

        google.afterGetPurchase = null
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken(), refundableQuantity = 0))
        reconciler.reconcile(NOW.plus(Duration.ofHours(1)))

        assertEquals(300_000, payment(TOKEN).refundedTokens)
        assertEquals(NOW.plus(Duration.ofHours(1)), recordRepository.lastSucceededAt(GoogleVoidedPurchaseReconciler.JOB_NAME))
    }

    @Test
    fun `배치가 실패하면 마지막 성공 시각을 그대로 두고, 다음 실행이 그 구간부터 다시 조회한다`() {
        val firstRun = NOW.minus(Duration.ofDays(3))
        reconciler.reconcile(firstRun)
        google.listVoidedUnavailable = true

        assertFailsWith<RuntimeException> { reconciler.reconcile(NOW.minus(Duration.ofDays(2))) }
        assertEquals(firstRun, recordRepository.lastSucceededAt(GoogleVoidedPurchaseReconciler.JOB_NAME))
        assertEquals(firstRun.epochSecond.toDouble(), lastSuccessGauge())

        google.listVoidedUnavailable = false
        google.listVoidedCalls.clear()
        reconciler.reconcile(NOW)

        assertEquals(firstRun.minus(GoogleVoidedPurchaseReconciler.OVERLAP), google.listVoidedCalls.single().first)
    }

    @Test
    fun `스케줄이 부른 회차가 실패하면 실패 횟수를 센다`() {
        google.listVoidedUnavailable = true
        val before = failureCount()

        reconciler.reconcile()

        assertEquals(1.0, failureCount() - before)
    }

    @Test
    fun `처음 실행하면 조회 가능한 최대 범위부터 본다`() {
        reconciler.reconcile(NOW)

        assertEquals(NOW.minus(GoogleVoidedPurchaseReconciler.MAX_LOOKBACK), google.listVoidedCalls.single().first)
    }

    @Test
    fun `마지막 성공이 조회 가능 범위보다 오래되면 최대 범위부터 보고 ERROR 를 남긴다`(output: CapturedOutput) {
        val stale = NOW.minus(Duration.ofDays(40))
        recordRepository.saveLastSucceededAt(GoogleVoidedPurchaseReconciler.JOB_NAME, stale)

        reconciler.reconcile(NOW)

        assertEquals(NOW.minus(GoogleVoidedPurchaseReconciler.MAX_LOOKBACK), google.listVoidedCalls.single().first)
        assertTrue(output.out.lines().any { "ERROR" in it && "조회 가능 범위보다 오래돼" in it && stale.toString() in it })
        assertEquals(NOW, recordRepository.lastSucceededAt(GoogleVoidedPurchaseReconciler.JOB_NAME))
    }

    /** Hibernate 는 Instant 를 UTC 로 저장한다. 처리 기록도 같은 기준이어야 같은 순간이 같은 값으로 남는다. */
    @Test
    fun `마지막 성공 시각은 UTC 로 저장한다`() {
        recordRepository.saveLastSucceededAt(GoogleVoidedPurchaseReconciler.JOB_NAME, Instant.parse("2026-11-12T00:00:00Z"))

        assertEquals(
            "2026-11-12 00:00:00",
            jdbcTemplate.queryForObject(
                "select date_format(last_succeeded_at, '%Y-%m-%d %H:%i:%s') from payment_job_checkpoint where job_name = ?",
                String::class.java,
                GoogleVoidedPurchaseReconciler.JOB_NAME,
            ),
        )
    }

    // ---- 도우미 ----

    private fun grant(
        token: String = TOKEN,
        messageId: String = "m-grant",
    ) {
        google.stub(token, purchase(accountToken = buyerAccountToken()))
        push(messageId, purchasedNotification(token = token)).andExpect { status { isNoContent() } }
        assertNotNull(paymentOrNull(token))
    }

    /** 검증기는 수량 1 만 지급하므로 다중 수량 결제는 지급 서비스로 직접 만든다. */
    private fun seedMultiQuantityPayment() {
        grantService.grantFromStoreNotification(
            VerifiedPurchase(
                platform = PaymentPlatform.GOOGLE,
                storeTransactionKey = StoreTransactionKey.ofGooglePurchaseToken(TOKEN_MULTI),
                accountToken = buyerAccountToken(),
                productId = "token_300000",
                quantity = 10,
                environment = PaymentEnvironment.PRODUCTION,
                purchasedAt = NOW,
                storeOrderId = "GPA.MULTI-0001",
                storePurchaseToken = TOKEN_MULTI,
            ),
        )
        assertEquals(3_000_000, payment(TOKEN_MULTI).grantedTokens)
    }

    private fun spend(amount: Long) {
        walletService.apply(
            TokenWalletChange(subjectKey = BUYER_SUBJECT, type = TokenLedgerType.CONSUME, amount = TokenAmount(amount), refId = null),
        )
    }

    private fun voided(
        token: String = TOKEN,
        orderId: String = "GPA.0000-0000-0000-00000",
        voidedTime: Instant = NOW.minus(Duration.ofHours(1)),
        reason: Int = 0,
        quantity: Int? = null,
    ) = GoogleVoidedPurchase(
        purchaseToken = token,
        orderId = orderId,
        voidedTime = voidedTime,
        voidedSource = 0,
        voidedReason = reason,
        voidedQuantity = quantity,
    )

    private fun push(
        messageId: String,
        notificationJson: String,
        authorization: String? = "Bearer $VALID_PUSH_TOKEN",
    ): ResultActionsDsl =
        pushRaw(
            """{"message": {"data": "${base64(
                notificationJson,
            )}", "messageId": "$messageId"}, "subscription": "projects/p/subscriptions/s"}""",
            authorization,
        )

    private fun pushRaw(
        body: String,
        authorization: String? = "Bearer $VALID_PUSH_TOKEN",
    ): ResultActionsDsl =
        mockMvc.post(WEBHOOK) {
            authorization?.let { header(HttpHeaders.AUTHORIZATION, it) }
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun base64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray())

    private fun testNotification() =
        """{"version": "1.0", "packageName": "$PACKAGE", "eventTimeMillis": "1", "testNotification": {"version": "1.0"}}"""

    private fun purchasedNotification(
        token: String = TOKEN,
        packageName: String = PACKAGE,
    ) = """{"version": "1.0", "packageName": "$packageName", "eventTimeMillis": "1",
           "oneTimeProductNotification": {"version": "1.0", "notificationType": 1, "purchaseToken": "$token", "sku": "token_300000"}}"""

    private fun voidedNotification(
        refundType: Int,
        token: String = TOKEN,
        productType: Int = 2,
    ) = """{"version": "1.0", "packageName": "$PACKAGE", "eventTimeMillis": "1",
           "voidedPurchaseNotification": {"purchaseToken": "$token", "orderId": "GPA.0000-0000-0000-00000",
             "productType": $productType, "refundType": $refundType}}"""

    private fun pendingRefundReviewNotification(orderId: String) =
        """{"version": "1.0", "packageName": "$PACKAGE", "eventTimeMillis": "1",
           "pendingRefundReviewNotification": {"orderId": "$orderId", "pendingRefundToken": "$REVIEW_TOKEN", "refundReason": 7}}"""

    private fun buyerAccountToken(): String = accountService.accountTokenOf(buyer.id).value

    private fun paymentOrNull(token: String) =
        paymentRepository.findByPlatformAndStoreTransactionKey(
            PaymentPlatform.GOOGLE,
            StoreTransactionKey.ofGooglePurchaseToken(token).value,
        )

    private fun payment(token: String) = requireNotNull(paymentOrNull(token))

    private fun lastSuccessGauge(): Double = meterRegistry.get(GoogleVoidedPurchaseReconciler.LAST_SUCCESS_METRIC).gauge().value()

    private fun failureCount(): Double = meterRegistry.counter(GoogleVoidedPurchaseReconciler.FAILURE_METRIC).count()

    private companion object {
        const val WEBHOOK = "/api/webhooks/google"
        const val VALID_PUSH_TOKEN = "valid-google-oidc-token"
        const val UNAVAILABLE_PUSH_TOKEN = "certs-unavailable"
        const val TOKEN = "google-purchase-token-for-refund-test"
        const val TOKEN_2 = "google-purchase-token-for-refund-test-2"
        const val TOKEN_MULTI = "google-purchase-token-for-refund-test-multi"
        const val REVIEW_TOKEN = "secret-review-token"
        val KEY = StoreTransactionKey.ofGooglePurchaseToken(TOKEN)
        val BUYER_SUBJECT = "f".repeat(64)
        val NOW: Instant = Instant.parse("2026-11-12T00:00:00Z")
    }
}

private const val PACKAGE = "com.nexters.gamss.test"
