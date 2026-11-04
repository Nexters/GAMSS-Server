package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.payment.domain.PaymentEnvironment
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.domain.VerifiedPurchase
import com.nexters.gamss.payment.repository.PaymentAccountRepository
import com.nexters.gamss.payment.repository.PaymentRepository
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.wallet.domain.TokenAmount
import com.nexters.gamss.wallet.domain.TokenLedgerType
import com.nexters.gamss.wallet.domain.TokenWalletChange
import com.nexters.gamss.wallet.repository.TokenLedgerRepository
import com.nexters.gamss.wallet.repository.TokenWalletRepository
import com.nexters.gamss.wallet.service.TokenWalletReader
import com.nexters.gamss.wallet.service.TokenWalletService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 지급과 회수는 트랜잭션 안에서 부를 수 없으므로([PurchaseGrantService]) 테스트 트랜잭션 롤백을 쓰지
 * 않는다. 운영과 같이 호출마다 커밋되고, 끝나면 직접 정리한다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
@ExtendWith(OutputCaptureExtension::class)
class PurchaseGrantServiceIntegrationTest {
    @Autowired
    private lateinit var grantService: PurchaseGrantService

    @Autowired
    private lateinit var accountService: PaymentAccountService

    @Autowired
    private lateinit var identityRepository: MemberSocialIdentityRepository

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
    private lateinit var walletService: TokenWalletService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private val createdMemberIds = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        identityRepository.upsert(BUYER, BUYER_SUBJECT)
        identityRepository.upsert(OTHER, OTHER_SUBJECT)
    }

    @AfterEach
    fun cleanUp() {
        ledgerRepository.deleteAll()
        walletRepository.deleteAll()
        paymentRepository.deleteAll()
        accountRepository.deleteAll()
        identityRepository.deleteAll()
        memberRepository.deleteAllById(createdMemberIds)
        jdbcTemplate.update("delete from token_product where product_id = ?", RETIRED_PRODUCT)
        jdbcTemplate.update("update token_product set active = true where product_id = 'token_300000'")
    }

    @Test
    fun `탈퇴한 회원의 구매는 지급하지 않는다`() {
        val withdrawn = savedMember(withdrawn = true)

        val error = assertFailsWith<BusinessException> { grantService.grant(withdrawn, purchase()) }

        assertEquals(ErrorCode.WITHDRAWN_MEMBER, error.errorCode)
        assertEquals(0, paymentRepository.count())
        assertEquals(0, walletReader.balanceOf(BUYER))
    }

    @Test
    fun `주체 매핑이 없는 활성 회원은 계정 토큰이 없는 구매여도 매핑 문제로 먼저 거부된다`() {
        val unmapped = savedMember(withdrawn = false)

        assertFailsWith<IllegalStateException> { grantService.grant(unmapped, purchase(accountToken = null)) }

        assertEquals(0, paymentRepository.count())
    }

    @Test
    fun `수량이 3 이면 상품 지급량의 3 배를 지급하고 결제와 원장에 남긴다`() {
        val result = grantService.grant(BUYER, purchase(quantity = 3))

        assertFalse(result.replayed)
        assertEquals(900_000, result.grantedTokens)
        assertEquals(900_000, result.balance)
        assertEquals(900_000, walletReader.balanceOf(BUYER))
        val payment = paymentRepository.findById(result.paymentId).get()
        assertEquals(BUYER_SUBJECT, payment.subjectKey)
        assertEquals(900_000, payment.grantedTokens)
        assertEquals(TokenLedgerType.PURCHASE, ledgerRepository.findByRefId("purchase:APPLE:tx-1")?.type)
    }

    @Test
    fun `같은 거래가 다시 오면 새로 지급하지 않고 처음 결과를 돌려준다`() {
        val first = grantService.grant(BUYER, purchase())

        val second = grantService.grant(BUYER, purchase())

        assertTrue(second.replayed)
        assertEquals(first.paymentId, second.paymentId)
        assertEquals(300_000, second.grantedTokens)
        assertEquals(300_000, walletReader.balanceOf(BUYER))
        assertEquals(1, paymentRepository.count())
        assertEquals(1, ledgerRepository.count())
    }

    @Test
    fun `지급 뒤 상품 판매를 멈춰도 같은 거래의 재요청은 성공한다`() {
        grantService.grant(BUYER, purchase())
        jdbcTemplate.update("update token_product set active = false where product_id = 'token_300000'")

        assertTrue(grantService.grant(BUYER, purchase()).replayed)
    }

    @Test
    fun `같은 거래 키로 다른 상품이 오면 키를 만든 쪽의 버그로 보고 거부한다`() {
        grantService.grant(BUYER, purchase())

        assertFailsWith<IllegalStateException> { grantService.grant(BUYER, purchase(productId = "token_500000")) }
        assertEquals(300_000, walletReader.balanceOf(BUYER))
    }

    @Test
    fun `다른 회원의 계정 토큰이 담긴 구매는 지급하지 않고 WARN 으로 남긴다`(output: CapturedOutput) {
        val othersToken = accountService.accountTokenOf(OTHER).value

        val error =
            assertFailsWith<BusinessException> {
                grantService.grant(BUYER, purchase(accountToken = othersToken, storeOrderId = "GPA.0001"))
            }

        assertEquals(ErrorCode.PURCHASE_ACCESS_DENIED, error.errorCode)
        assertEquals(0, paymentRepository.count())
        assertEquals(0, walletReader.balanceOf(BUYER))
        assertEquals(0, walletReader.balanceOf(OTHER))
        assertTrue(output.all.lines().any { "WARN" in it && "storeOrderId=GPA.0001" in it && "requesterMemberId=$BUYER" in it })
    }

    @Test
    fun `계정 토큰이 없거나 발급한 적 없는 값이면 지급하지 않고 ERROR 로 남긴다`(output: CapturedOutput) {
        accountService.accountTokenOf(BUYER)

        listOf(null, "not-a-uuid", "3f2b8c1e-7a4d-4e6b-9c0a-1d2e3f4a5b6c").forEachIndexed { i, token ->
            val error =
                assertFailsWith<BusinessException> {
                    grantService.grant(BUYER, purchase(key = "tx-$i", accountToken = token, quantity = 2, storeOrderId = "GPA.100$i"))
                }
            assertEquals(ErrorCode.PURCHASE_ACCESS_DENIED, error.errorCode, "token=$token")
            assertTrue(
                output.all.lines().any {
                    "ERROR" in it && "key=tx-$i," in it && "storeOrderId=GPA.100$i," in it &&
                        "productId=token_300000," in it && "quantity=2," in it
                },
                "ERROR 로그가 없다: token=$token",
            )
        }
        assertEquals(0, paymentRepository.count())
    }

    @Test
    fun `카탈로그에 없거나 비활성인 상품은 지급하지 않고 수동 처리에 필요한 값을 ERROR 로 남긴다`(output: CapturedOutput) {
        insertProduct(RETIRED_PRODUCT, 100, active = false)

        listOf("token_unknown", RETIRED_PRODUCT).forEachIndexed { i, productId ->
            val error =
                assertFailsWith<BusinessException> {
                    grantService.grant(BUYER, purchase(key = "tx-$i", productId = productId, quantity = 2, storeOrderId = "GPA.000$i"))
                }
            assertEquals(ErrorCode.PAYMENT_PRODUCT_UNAVAILABLE, error.errorCode)
            assertTrue(
                output.all.lines().any {
                    "ERROR" in it && "productId=$productId," in it && "storeOrderId=GPA.000$i," in it && "quantity=2," in it &&
                        "subjectKey=$BUYER_SUBJECT" in it
                },
                "ERROR 로그가 없다: $productId",
            )
        }
        assertEquals(0, paymentRepository.count())
        assertEquals(0, walletReader.balanceOf(BUYER))
    }

    @Test
    fun `스토어 알림으로만 온 구매는 계정 토큰의 주인에게 지급한다`() {
        val result = grantService.grantFromStoreNotification(purchase())

        assertEquals(300_000, result.balance)
        assertEquals(300_000, walletReader.balanceOf(BUYER))
    }

    @Test
    fun `스토어 알림의 계정 토큰이 발급한 적 없는 값이면 주인을 찾지 못하고 ERROR 로 남긴다`(output: CapturedOutput) {
        val error =
            assertFailsWith<BusinessException> {
                grantService.grantFromStoreNotification(
                    purchase(accountToken = "3f2b8c1e-7a4d-4e6b-9c0a-1d2e3f4a5b6c", storeOrderId = "GPA.2000"),
                )
            }

        assertEquals(ErrorCode.PAYMENT_ACCOUNT_NOT_FOUND, error.errorCode)
        assertTrue(
            output.all.lines().any {
                "ERROR" in it && "key=tx-1," in it && "storeOrderId=GPA.2000," in it && "requesterMemberId=null" in it
            },
        )
    }

    /**
     * 예외를 바깥 트랜잭션 안에서 잡아 그 트랜잭션이 커밋되게 한다. 밖으로 던지면 롤백이 쓴 것을 지워서,
     * 가드가 작업 뒤로 밀려 먼저 쓰고 던지는 퇴행이 생겨도 뒤의 단언이 통과해 버린다.
     */
    @Test
    fun `트랜잭션 안에서 부르면 아무것도 쓰기 전에 지급과 회수를 모두 거부한다`() {
        grantService.grant(BUYER, purchase())
        val tx2 = purchase(key = "tx-2")
        val tx3 = purchase(key = "tx-3")

        transactionTemplate.executeWithoutResult {
            listOf(
                { grantService.grant(BUYER, tx2) },
                { grantService.grantFromStoreNotification(tx3) },
                { grantService.applyRefund(PaymentPlatform.APPLE, KEY, 300_000, "refund") },
            ).forEach { call ->
                val error = assertFailsWith<IllegalStateException> { call() }
                assertTrue("트랜잭션 안에서 부를 수 없습니다" in error.message.orEmpty(), error.message)
            }
        }

        assertEquals(1, paymentRepository.count())
        assertEquals(300_000, walletReader.balanceOf(BUYER))
        assertEquals(1, ledgerRepository.count())
    }

    @Test
    fun `같은 회수 목표로 두 번 불러도 한 번만 회수한다`() {
        grantService.grant(BUYER, purchase())

        val first = grantService.applyRefund(PaymentPlatform.APPLE, KEY, 300_000, "notification-1")
        val second = grantService.applyRefund(PaymentPlatform.APPLE, KEY, 300_000, "notification-2")

        assertEquals(300_000, first.changedTokens)
        assertEquals(0, second.changedTokens)
        assertEquals(0, walletReader.balanceOf(BUYER))
        assertNull(ledgerRepository.findByRefId("refund:APPLE:tx-1:notification-2"))
    }

    @Test
    fun `전액 환불 후 환불 취소를 하면 원래 잔액으로 돌아온다`() {
        grantService.grant(BUYER, purchase())

        grantService.applyRefund(PaymentPlatform.APPLE, KEY, 300_000, "refund")
        val reversed = grantService.applyRefund(PaymentPlatform.APPLE, KEY, 0, "reversal")

        assertEquals(-300_000, reversed.changedTokens)
        assertEquals(0, reversed.refundedTokens)
        assertEquals(300_000, walletReader.balanceOf(BUYER))
        assertEquals(TokenLedgerType.REFUND, ledgerRepository.findByRefId("refund:APPLE:tx-1:refund")?.type)
        assertEquals(TokenLedgerType.REFUND_REVERSED, ledgerRepository.findByRefId("refund:APPLE:tx-1:reversal")?.type)
    }

    @Test
    fun `이미 써 버린 토큰을 환불하면 잔액이 음수가 된다`() {
        grantService.grant(BUYER, purchase())
        walletService.apply(
            TokenWalletChange(subjectKey = BUYER_SUBJECT, type = TokenLedgerType.CONSUME, amount = TokenAmount(200_000), refId = null),
        )

        grantService.applyRefund(PaymentPlatform.APPLE, KEY, 300_000, "refund")

        assertEquals(-200_000, walletReader.balanceOf(BUYER))
    }

    @Test
    fun `잔액을 바꾼 환불 출처는 환불 취소 뒤 다시 와도 결제 기록과 잔액을 바꾸지 않는다`() {
        grantService.grant(BUYER, purchase())
        grantService.applyRefund(PaymentPlatform.APPLE, KEY, 300_000, "n1")
        grantService.applyRefund(PaymentPlatform.APPLE, KEY, 0, "n2")

        val replayed = grantService.applyRefund(PaymentPlatform.APPLE, KEY, 300_000, "n1")

        assertEquals(0, replayed.changedTokens)
        assertEquals(0, replayed.refundedTokens)
        assertEquals(300_000, walletReader.balanceOf(BUYER))
        assertEquals(2, ledgerRepository.findAll().count { it.refId?.startsWith("refund:") == true })
    }

    /**
     * 출처 중복 제거는 호출부의 계약이다([PurchaseGrantService.applyRefund]). 원장은 0 인 변동을 받지
     * 않으므로 차이 0 으로 끝난 출처는 흔적이 없고, 여기서는 새 출처와 구분하지 못한다. 이 테스트가
     * 깨진다면 출처를 따로 기억하게 된 것이니 KDoc 의 계약도 함께 고친다.
     */
    @Test
    fun `차이 0 으로 끝난 환불 출처는 기억하지 않아 환불 취소 뒤 다시 오면 다시 회수한다`() {
        grantService.grant(BUYER, purchase())
        grantService.applyRefund(PaymentPlatform.APPLE, KEY, 300_000, "batch-1")
        assertEquals(0, grantService.applyRefund(PaymentPlatform.APPLE, KEY, 300_000, "n1").changedTokens)
        grantService.applyRefund(PaymentPlatform.APPLE, KEY, 0, "n2")

        val again = grantService.applyRefund(PaymentPlatform.APPLE, KEY, 300_000, "n1")

        assertEquals(300_000, again.changedTokens)
        assertEquals(0, walletReader.balanceOf(BUYER))
    }

    @Test
    fun `전액 환불 뒤 옛 부분 환불 알림이 다시 와도 실패하지 않고 아무것도 바꾸지 않는다`() {
        grantService.grant(BUYER, purchase())
        grantService.applyRefund(PaymentPlatform.APPLE, KEY, 100_000, "partial")
        grantService.applyRefund(PaymentPlatform.APPLE, KEY, 300_000, "full")

        val replayed = grantService.applyRefund(PaymentPlatform.APPLE, KEY, 100_000, "partial")

        assertEquals(0, replayed.changedTokens)
        assertEquals(300_000, replayed.refundedTokens)
        assertEquals(0, walletReader.balanceOf(BUYER))
    }

    @Test
    fun `결제 기록이 없는 거래의 환불은 찾지 못한다`() {
        val error = assertFailsWith<BusinessException> { grantService.applyRefund(PaymentPlatform.APPLE, KEY, 1, "refund") }

        assertEquals(ErrorCode.PAYMENT_NOT_FOUND, error.errorCode)
    }

    private fun purchase(
        key: String = KEY.value,
        accountToken: String? = accountService.accountTokenOf(BUYER).value,
        productId: String = "token_300000",
        quantity: Int = 1,
        storeOrderId: String? = null,
    ) = VerifiedPurchase(
        platform = PaymentPlatform.APPLE,
        storeTransactionKey = StoreTransactionKey(key),
        accountToken = accountToken,
        productId = productId,
        quantity = quantity,
        environment = PaymentEnvironment.SANDBOX,
        purchasedAt = Instant.parse("2026-11-04T00:00:00Z"),
        storeOrderId = storeOrderId,
    )

    private fun savedMember(withdrawn: Boolean): Long {
        val member = Member("payment-${createdMemberIds.size}@a.com")
        if (withdrawn) {
            member.withdraw()
        }
        return memberRepository.save(member).id.also { createdMemberIds += it }
    }

    private fun insertProduct(
        productId: String,
        amount: Long,
        active: Boolean,
    ) {
        jdbcTemplate.update(
            "insert into token_product (product_id, token_amount, active, updated_at) values (?, ?, ?, now(6))",
            productId,
            amount,
            active,
        )
    }

    private companion object {
        const val BUYER = 9_001L
        const val OTHER = 9_002L
        const val RETIRED_PRODUCT = "token_retired"
        val BUYER_SUBJECT = "a".repeat(64)
        val OTHER_SUBJECT = "b".repeat(64)
        val KEY = StoreTransactionKey("tx-1")
    }
}
