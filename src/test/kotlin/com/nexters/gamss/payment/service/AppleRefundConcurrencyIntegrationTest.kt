package com.nexters.gamss.payment.service

import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.payment.domain.AppleRefundState
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.repository.AppleNotificationRecordRepository
import com.nexters.gamss.payment.repository.PaymentAccountRepository
import com.nexters.gamss.payment.repository.PaymentRepository
import com.nexters.gamss.support.FakeAppleTransactionDecoder
import com.nexters.gamss.support.FakeAppleTransactionDecoderConfig
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.wallet.repository.TokenLedgerRepository
import com.nexters.gamss.wallet.repository.TokenWalletRepository
import com.nexters.gamss.wallet.service.TokenWalletReader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * App Store 환불 상태 반영([AppleRefundSynchronizer])의 경합을 실제 커밋끼리 부딪쳐 확인한다(#266 리뷰).
 *
 * - 상태는 결제 행 잠금 안에서 읽는다: 테스트가 잠금을 쥐고 순서를 정한다. 잠그기 전에 읽으면 기다리는 사이 커밋된
 *   최신 상태 대신 옛 상태로 회수한다.
 * - 지급과 환불: 환불 쪽은 상태 커밋 후 결제를, 지급 쪽은 결제 커밋 후 상태를 보므로 적어도 한쪽은 회수한다.
 *   타이밍에 기대므로 여러 회차를 돌린다.
 */
@SpringBootTest(properties = ["spring.jpa.open-in-view=false"])
@Import(TestcontainersConfig::class, FakeAppleTransactionDecoderConfig::class)
class AppleRefundConcurrencyIntegrationTest {
    @Autowired private lateinit var synchronizer: AppleRefundSynchronizer

    @Autowired private lateinit var purchaseService: ApplePurchaseService

    @Autowired private lateinit var transactions: FakeAppleTransactionDecoder

    @Autowired private lateinit var accountService: PaymentAccountService

    @Autowired private lateinit var walletReader: TokenWalletReader

    @Autowired private lateinit var identityRepository: MemberSocialIdentityRepository

    @Autowired private lateinit var accountRepository: PaymentAccountRepository

    @Autowired private lateinit var paymentRepository: PaymentRepository

    @Autowired private lateinit var walletRepository: TokenWalletRepository

    @Autowired private lateinit var ledgerRepository: TokenLedgerRepository

    @Autowired private lateinit var recordRepository: AppleNotificationRecordRepository

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        transactions.reset()
        identityRepository.upsert(BUYER, SUBJECT)
    }

    @AfterEach
    fun cleanUp() {
        ledgerRepository.deleteAll()
        walletRepository.deleteAll()
        paymentRepository.deleteAll()
        accountRepository.deleteAll()
        identityRepository.deleteAll()
        jdbcTemplate.update("delete from apple_refund_state")
    }

    @Test
    fun `잠금을 기다리는 사이 더 최신 상태가 커밋되면 그 상태로 회수량을 맞춘다`() {
        val transactionId = transactionId(0)
        purchaseService.purchase(BUYER, stubPurchase(transactionId))
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val holder =
                executor.submit {
                    TransactionTemplate(transactionManager).executeWithoutResult {
                        paymentRepository.findByPlatformAndStoreTransactionKeyForUpdate(PaymentPlatform.APPLE, transactionId.value)
                        locked.countDown()
                        release.await(30, TimeUnit.SECONDS)
                    }
                }
            locked.await(30, TimeUnit.SECONDS)
            val refund =
                executor.submit<RefundResult?> {
                    synchronizer.record(transactionId, AppleRefundState.refunded(null), SIGNED_AT, "refund")
                }
            // 환불 쪽이 상태를 저장하고 잠금을 기다리는 사이, 환불 취소가 더 최신 상태를 커밋한다.
            Thread.sleep(500)
            recordRepository.saveRefundStateIfNewer(
                transactionId,
                AppleRefundState.NOT_REFUNDED,
                SIGNED_AT.plusSeconds(60),
                "reversed",
                Instant.now(),
            )
            release.countDown()
            holder.get(30, TimeUnit.SECONDS)
            refund.get(30, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }

        val payment = paymentRepository.findByPlatformAndStoreTransactionKey(PaymentPlatform.APPLE, transactionId.value)
        assertEquals(0, payment?.refundedTokens)
        assertEquals(GRANTED, walletReader.balanceOf(BUYER))
    }

    @Test
    fun `지급과 환불이 동시에 와도 회수를 놓치지 않는다`() {
        repeat(ROUNDS) { round ->
            val transactionId = transactionId(round + 1)
            val jws = stubPurchase(transactionId)
            runTogether(
                { purchaseService.purchase(BUYER, jws) },
                { synchronizer.record(transactionId, AppleRefundState.refunded(null), SIGNED_AT, "refund-$round") },
            )

            val payment = paymentRepository.findByPlatformAndStoreTransactionKey(PaymentPlatform.APPLE, transactionId.value)
            assertEquals(GRANTED, payment?.refundedTokens, "${round}회차 회수량")
            assertEquals(0, walletReader.balanceOf(BUYER), "${round}회차 잔액")
        }
    }

    private fun runTogether(vararg tasks: () -> Any?) {
        val startLine = CyclicBarrier(tasks.size)
        val executor = Executors.newFixedThreadPool(tasks.size)
        try {
            tasks
                .map { task ->
                    executor.submit<Any?> {
                        startLine.await()
                        task()
                    }
                }.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun stubPurchase(transactionId: StoreTransactionKey): String {
        val jws = "apple-purchase-${transactionId.value}"
        transactions.stub(
            jws,
            FakeAppleTransactionDecoder.transaction(
                accountToken = accountService.accountTokenOf(BUYER).value,
                transactionId = transactionId.value,
            ),
        )
        return jws
    }

    private fun transactionId(n: Int) = StoreTransactionKey("20000009%08d".format(n))

    private companion object {
        const val ROUNDS = 30
        const val GRANTED = 300_000L
        const val BUYER = 9_102L
        val SUBJECT = "f".repeat(64)
        val SIGNED_AT: Instant = Instant.parse("2026-11-08T00:00:00Z")
    }
}
