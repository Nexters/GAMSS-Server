package com.nexters.gamss.payment.controller

import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.payment.domain.PaymentEnvironment
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.repository.PaymentAccountRepository
import com.nexters.gamss.payment.repository.PaymentRepository
import com.nexters.gamss.payment.service.PaymentAccountService
import com.nexters.gamss.support.FakeAppleTransactionDecoder
import com.nexters.gamss.support.FakeAppleTransactionDecoder.Companion.transaction
import com.nexters.gamss.support.FakeAppleTransactionDecoderConfig
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.wallet.repository.TokenLedgerRepository
import com.nexters.gamss.wallet.repository.TokenWalletRepository
import com.nexters.gamss.wallet.service.TokenWalletReader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
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
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * App Store 구매 검증 API 를 지급까지 실제 DB 로 통과시킨다. 서명 검증만 가짜로 둔다.
 *
 * 지급은 트랜잭션 안에서 부를 수 없으므로 테스트 트랜잭션 롤백을 쓰지 않고, 끝나면 직접 정리한다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class, FakeAppleTransactionDecoderConfig::class)
class ApplePurchaseApiIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var apple: FakeAppleTransactionDecoder

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
        apple.reset()
        buyer = memberRepository.save(Member("apple-buyer@a.com"))
        other = memberRepository.save(Member("apple-other@a.com"))
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
    fun `거래를 검증해 지급하고 기록을 남긴다`() {
        apple.stub(JWS, transaction(accountToken = buyerAccountToken()))

        purchaseApple(buyer).andExpect {
            status { isOk() }
            jsonPath("$.data.productId") { value("token_300000") }
            jsonPath("$.data.grantedTokens") { value(300_000) }
            jsonPath("$.data.paidBalance") { value(300_000) }
        }

        val payment = savedPayment()
        assertEquals(BUYER_SUBJECT, payment.subjectKey)
        assertEquals(1, payment.quantity)
        assertEquals(PaymentEnvironment.PRODUCTION, payment.environment)
        assertEquals(Instant.parse("2026-11-06T00:00:00Z"), payment.purchasedAt)
        assertNull(payment.storePurchaseToken)
        assertNull(payment.consumedAt)
        assertEquals(300_000, walletReader.balanceOf(buyer.id))
    }

    @Test
    fun `Sandbox 거래도 지급하고 SANDBOX 로 기록한다`() {
        apple.stub(JWS, transaction(accountToken = buyerAccountToken(), environment = PaymentEnvironment.SANDBOX))

        purchaseApple(buyer).andExpect { status { isOk() } }

        assertEquals(PaymentEnvironment.SANDBOX, savedPayment().environment)
    }

    @Test
    fun `여러 개를 산 거래는 상품 지급량에 수량을 곱해 지급한다`() {
        apple.stub(JWS, transaction(accountToken = buyerAccountToken(), quantity = 3))

        purchaseApple(buyer).andExpect {
            status { isOk() }
            jsonPath("$.data.grantedTokens") { value(900_000) }
            jsonPath("$.data.paidBalance") { value(900_000) }
        }

        assertEquals(3, savedPayment().quantity)
    }

    @Test
    fun `같은 거래를 다시 보내면 추가 지급 없이 같은 성공 응답을 준다`() {
        apple.stub(JWS, transaction(accountToken = buyerAccountToken()))
        purchaseApple(buyer).andExpect { status { isOk() } }

        purchaseApple(buyer).andExpect {
            status { isOk() }
            jsonPath("$.data.grantedTokens") { value(300_000) }
            jsonPath("$.data.paidBalance") { value(300_000) }
        }

        assertEquals(1, paymentRepository.count())
        assertEquals(1, ledgerRepository.count())
        assertEquals(300_000, walletReader.balanceOf(buyer.id))
    }

    @Test
    fun `다른 회원의 계정 토큰이 담긴 거래는 finish 하지 않도록 403 으로 거부한다`() {
        apple.stub(JWS, transaction(accountToken = accountService.accountTokenOf(other.id).value))

        purchaseApple(buyer).andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("PURCHASE_ACCESS_DENIED") }
        }

        assertEquals(0, paymentRepository.count())
    }

    @Test
    fun `계정 토큰이 없는 거래는 403 으로 거부한다`() {
        apple.stub(JWS, transaction(accountToken = null))

        purchaseApple(buyer).andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("PURCHASE_ACCESS_DENIED") }
        }

        assertEquals(0, paymentRepository.count())
    }

    @Test
    fun `환불된 거래는 409 로 거부한다`() {
        apple.stub(JWS, transaction(accountToken = buyerAccountToken(), revocationDate = Instant.parse("2026-11-07T00:00:00Z")))

        purchaseApple(buyer).andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("PURCHASE_CANCELLED") }
        }

        assertEquals(0, paymentRepository.count())
    }

    @Test
    fun `판매를 멈춘 상품은 409 로 거부하지만 이미 지급한 거래의 재요청은 성공한다`() {
        apple.stub(JWS, transaction(accountToken = buyerAccountToken()))
        apple.stub(OTHER_JWS, transaction(accountToken = buyerAccountToken(), transactionId = "2000000999999999"))
        purchaseApple(buyer).andExpect { status { isOk() } }
        jdbcTemplate.update("update token_product set active = false where product_id = 'token_300000'")

        purchaseApple(buyer).andExpect { status { isOk() } }
        purchaseApple(buyer, OTHER_JWS).andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("PAYMENT_PRODUCT_UNAVAILABLE") }
        }
    }

    @Test
    fun `서명을 확인할 수 없는 거래는 400, 일시 장애는 503 으로 응답한다`() {
        purchaseApple(buyer).andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("PURCHASE_INVALID") }
        }

        apple.unavailable = true
        purchaseApple(buyer).andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.error.code") { value("PAYMENT_STORE_UNAVAILABLE") }
        }
    }

    @Test
    fun `요청 값이 비었거나 너무 길면 400 으로 거부한다`() {
        listOf(
            """{"signedTransaction": ""}""",
            """{}""",
            """{"signedTransaction": "${"j".repeat(16385)}"}""",
        ).forEach { body ->
            mockMvc
                .post(APPLE) {
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
            .post(APPLE) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"signedTransaction": "$JWS"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    private fun purchaseApple(
        member: Member,
        signedTransaction: String = JWS,
    ): ResultActionsDsl =
        mockMvc.post(APPLE) {
            header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            contentType = MediaType.APPLICATION_JSON
            content = """{"signedTransaction": "$signedTransaction"}"""
        }

    private fun buyerAccountToken(): String = accountService.accountTokenOf(buyer.id).value

    private fun savedPayment() =
        requireNotNull(paymentRepository.findByPlatformAndStoreTransactionKey(PaymentPlatform.APPLE, TRANSACTION_ID))

    private fun bearerFor(member: Member): String = "Bearer ${jwtIssuer.issueAccessToken(member.id)}"

    private companion object {
        const val APPLE = "/api/payments/apple"
        const val JWS = "apple-signed-transaction-for-api-test"
        const val OTHER_JWS = "apple-signed-transaction-other"
        const val TRANSACTION_ID = "2000000123456789"
        val BUYER_SUBJECT = "f".repeat(64)
        val OTHER_SUBJECT = "0".repeat(64)
    }
}
