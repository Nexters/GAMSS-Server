package com.nexters.gamss.payment.controller

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.payment.domain.PaymentEnvironment
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.google.GoogleConsumptionState
import com.nexters.gamss.payment.google.GooglePurchaseState
import com.nexters.gamss.payment.repository.PaymentAccountRepository
import com.nexters.gamss.payment.repository.PaymentRepository
import com.nexters.gamss.payment.service.GooglePurchaseConsumer
import com.nexters.gamss.payment.service.PaymentAccountService
import com.nexters.gamss.support.FakeGooglePlayClient
import com.nexters.gamss.support.FakeGooglePlayClient.Companion.purchase
import com.nexters.gamss.support.FakeGooglePlayClientConfig
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.wallet.repository.TokenLedgerRepository
import com.nexters.gamss.wallet.repository.TokenWalletRepository
import com.nexters.gamss.wallet.service.TokenWalletReader
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Google 구매 검증 API 를 지급·consume 까지 실제 DB 로 통과시킨다. Google 만 가짜로 둔다.
 *
 * 지급은 트랜잭션 안에서 부를 수 없으므로([com.nexters.gamss.payment.service.PurchaseGrantService])
 * 테스트 트랜잭션 롤백을 쓰지 않는다. 운영처럼 호출마다 커밋되고, 끝나면 직접 정리한다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class, FakeGooglePlayClientConfig::class)
@ExtendWith(OutputCaptureExtension::class)
class GooglePurchaseApiIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var google: FakeGooglePlayClient

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var identityRepository: MemberSocialIdentityRepository

    @Autowired
    private lateinit var accountService: PaymentAccountService

    @Autowired
    private lateinit var accountRepository: PaymentAccountRepository

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var walletRepository: TokenWalletRepository

    @Autowired
    private lateinit var ledgerRepository: TokenLedgerRepository

    @Autowired
    private lateinit var walletReader: TokenWalletReader

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var jwtIssuer: JwtIssuer

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private lateinit var mockMvc: MockMvc
    private lateinit var buyer: Member
    private lateinit var other: Member

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        google.reset()
        buyer = memberRepository.save(Member("google-buyer@a.com"))
        other = memberRepository.save(Member("google-other@a.com"))
        identityRepository.upsert(buyer.id, BUYER_SUBJECT)
        identityRepository.upsert(other.id, OTHER_SUBJECT)
    }

    @AfterEach
    fun cleanUp() {
        ledgerRepository.deleteAll()
        walletRepository.deleteAll()
        paymentRepository.deleteAll()
        accountRepository.deleteAll()
        identityRepository.deleteAll()
        memberRepository.deleteAllById(listOf(buyer.id, other.id))
        jdbcTemplate.update("update token_product set active = true where product_id = 'token_300000'")
    }

    @Test
    fun `구매를 검증해 지급하고 consume 한 뒤 기록을 남긴다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken()))

        purchaseGoogle(buyer).andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("GRANTED") }
            jsonPath("$.data.productId") { value("token_300000") }
            jsonPath("$.data.grantedTokens") { value(300_000) }
            jsonPath("$.data.paidBalance") { value(300_000) }
        }

        val payment = paymentRepository.findByPlatformAndStoreTransactionKey(PaymentPlatform.GOOGLE, KEY.value)
        assertNotNull(payment)
        assertEquals(TOKEN, payment.storePurchaseToken)
        assertEquals("GPA.0000-0000-0000-00000", payment.storeOrderId)
        assertEquals(PaymentEnvironment.PRODUCTION, payment.environment)
        assertNotNull(payment.consumedAt)
        assertEquals(listOf("token_300000" to TOKEN), google.consumeCalls)
        assertEquals(listOf(false), google.consumeInTransaction)
        assertEquals(300_000, walletReader.balanceOf(buyer.id))
    }

    @Test
    fun `테스트 구매는 SANDBOX 로 기록한다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken(), testPurchase = true))

        purchaseGoogle(buyer).andExpect { status { isOk() } }

        assertEquals(PaymentEnvironment.SANDBOX, savedPayment().environment)
    }

    @Test
    fun `consume 이 실패해도 지급은 성공으로 응답하고 consume 기록은 비워 둔다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken()))
        google.consumeFailure = BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
        val failuresBefore = consumeFailureCount()

        purchaseGoogle(buyer).andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("GRANTED") }
            jsonPath("$.data.grantedTokens") { value(300_000) }
        }

        assertNull(savedPayment().consumedAt)
        assertEquals(300_000, walletReader.balanceOf(buyer.id))
        assertEquals(1.0, consumeFailureCount() - failuresBefore)
    }

    /** 이미 지급한 구매를 실패로 돌려주면 앱은 지급 완료를 표시하지 못하고 같은 구매를 다시 보낸다. */
    @Test
    fun `consume 단계에서 예상하지 못한 오류가 나도 지급은 성공으로 응답한다`(output: CapturedOutput) {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken()))
        google.consumeFailure = IllegalStateException("unexpected")

        purchaseGoogle(buyer).andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("GRANTED") }
        }

        assertNull(savedPayment().consumedAt)
        assertTrue(output.out.lines().any { "ERROR" in it && "consume 처리 중 예상하지 못한 오류" in it })
    }

    @Test
    fun `이미 consume 된 구매는 consume 을 다시 부르지 않고 기록만 채운다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken(), consumption = GoogleConsumptionState.CONSUMED))

        purchaseGoogle(buyer).andExpect { status { isOk() } }

        assertTrue(google.consumeCalls.isEmpty())
        assertNotNull(savedPayment().consumedAt)
    }

    @Test
    fun `같은 구매를 다시 보내면 추가 지급 없이 같은 성공 응답을 준다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken()))
        purchaseGoogle(buyer).andExpect { status { isOk() } }

        purchaseGoogle(buyer).andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("GRANTED") }
            jsonPath("$.data.grantedTokens") { value(300_000) }
            jsonPath("$.data.paidBalance") { value(300_000) }
        }

        assertEquals(1, paymentRepository.count())
        assertEquals(1, ledgerRepository.count())
        assertEquals(300_000, walletReader.balanceOf(buyer.id))
    }

    @Test
    fun `consume 이 실패한 구매를 다시 보내면 이번에 consume 한다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken()))
        google.consumeFailure = BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
        purchaseGoogle(buyer).andExpect { status { isOk() } }
        google.consumeFailure = null

        purchaseGoogle(buyer).andExpect { status { isOk() } }

        assertNotNull(savedPayment().consumedAt)
        assertEquals(1, ledgerRepository.count())
    }

    @Test
    fun `판매를 멈춘 상품이어도 이미 지급한 구매의 재요청은 성공한다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken()))
        purchaseGoogle(buyer).andExpect { status { isOk() } }
        jdbcTemplate.update("update token_product set active = false where product_id = 'token_300000'")

        purchaseGoogle(buyer).andExpect {
            status { isOk() }
            jsonPath("$.data.grantedTokens") { value(300_000) }
        }
    }

    @Test
    fun `PENDING 구매는 지급하지 않고 대기로 응답한다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken(), state = GooglePurchaseState.PENDING))

        purchaseGoogle(buyer).andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("PENDING") }
            jsonPath("$.data.productId") { value("token_300000") }
            jsonPath("$.data.grantedTokens") { value(null) }
            jsonPath("$.data.paidBalance") { value(null) }
        }

        assertEquals(0, paymentRepository.count())
        assertEquals(0, ledgerRepository.count())
        assertTrue(google.consumeCalls.isEmpty())
    }

    @Test
    fun `다른 회원의 계정 토큰이 담긴 구매는 거부하고 WARN 으로 남긴다`(output: CapturedOutput) {
        google.stub(TOKEN, purchase(accountToken = accountService.accountTokenOf(other.id).value))

        purchaseGoogle(buyer).andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("PURCHASE_ACCESS_DENIED") }
        }

        assertEquals(0, paymentRepository.count())
        assertTrue(google.consumeCalls.isEmpty())
        assertTrue(output.out.lines().any { "WARN" in it && "다른 주체의 계정 토큰이 담긴 구매" in it })
    }

    @Test
    fun `계정 토큰이 없는 구매는 거부하고 수동 처리 대상으로 ERROR 를 남긴다`(output: CapturedOutput) {
        google.stub(TOKEN, purchase(accountToken = null))

        purchaseGoogle(buyer).andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("PURCHASE_ACCESS_DENIED") }
        }

        assertEquals(0, paymentRepository.count())
        assertTrue(output.out.lines().any { "ERROR" in it && "계정 토큰으로 주인을 찾지 못해" in it })
        assertFalse(output.out.contains(TOKEN))
    }

    @Test
    fun `취소된 구매는 409 로 거부한다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken(), state = GooglePurchaseState.CANCELLED))

        purchaseGoogle(buyer).andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("PURCHASE_CANCELLED") }
        }
    }

    @Test
    fun `여러 개를 산 구매는 409 로 거부한다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken(), quantity = 2))

        purchaseGoogle(buyer).andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("PURCHASE_QUANTITY_UNSUPPORTED") }
        }

        assertEquals(0, paymentRepository.count())
    }

    @Test
    fun `구매한 상품이 요청과 다르면 400 으로 거부한다`() {
        google.stub(TOKEN, purchase(accountToken = buyerAccountToken(), productId = "token_1000000"))

        purchaseGoogle(buyer).andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("PURCHASE_PRODUCT_MISMATCH") }
        }
    }

    @Test
    fun `Google 이 모르는 토큰이면 400 으로 거부한다`() {
        purchaseGoogle(buyer).andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("PURCHASE_INVALID") }
        }
    }

    @Test
    fun `요청 값이 비었거나 너무 길면 400 으로 거부한다`() {
        listOf(
            """{"productId": "", "purchaseToken": "t"}""",
            """{"productId": "token_300000", "purchaseToken": " "}""",
            """{"productId": "token_300000"}""",
            """{"productId": "token_300000", "purchaseToken": "${"t".repeat(4097)}"}""",
        ).forEach { body ->
            mockMvc
                .post(GOOGLE) {
                    header(HttpHeaders.AUTHORIZATION, bearerFor(buyer))
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVALID_INPUT") }
                }
        }
    }

    @Test
    fun `인증 없이 부르면 401 을 반환한다`() {
        mockMvc
            .post(GOOGLE) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"productId": "token_300000", "purchaseToken": "$TOKEN"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    private fun purchaseGoogle(member: Member): ResultActionsDsl =
        mockMvc.post(GOOGLE) {
            header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            contentType = MediaType.APPLICATION_JSON
            content = """{"productId": "token_300000", "purchaseToken": "$TOKEN"}"""
        }

    private fun buyerAccountToken(): String = accountService.accountTokenOf(buyer.id).value

    private fun consumeFailureCount(): Double = meterRegistry.counter(GooglePurchaseConsumer.FAILURE_METRIC).count()

    private fun savedPayment() = requireNotNull(paymentRepository.findByPlatformAndStoreTransactionKey(PaymentPlatform.GOOGLE, KEY.value))

    private fun bearerFor(member: Member): String = "Bearer ${jwtIssuer.issueAccessToken(member.id)}"

    private companion object {
        const val GOOGLE = "/api/payments/google"
        const val TOKEN = "google-purchase-token-for-api-test"
        val KEY = StoreTransactionKey.ofGooglePurchaseToken(TOKEN)
        val BUYER_SUBJECT = "d".repeat(64)
        val OTHER_SUBJECT = "e".repeat(64)
    }
}
