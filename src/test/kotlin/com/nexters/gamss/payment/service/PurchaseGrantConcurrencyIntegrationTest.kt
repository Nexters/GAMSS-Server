package com.nexters.gamss.payment.service

import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.payment.domain.PaymentEnvironment
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.domain.VerifiedPurchase
import com.nexters.gamss.payment.repository.PaymentAccountRepository
import com.nexters.gamss.payment.repository.PaymentRepository
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.wallet.repository.TokenLedgerRepository
import com.nexters.gamss.wallet.repository.TokenWalletRepository
import com.nexters.gamss.wallet.service.TokenWalletReader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 결제의 세 경합을 실제 커밋끼리 부딪쳐 확인한다. 테스트 트랜잭션 롤백을 쓰지 않고 직접 정리한다.
 *
 * - 지급: `(platform, store_transaction_key)` 유니크가 두 번째 저장을 막고, [PurchaseGrantService] 의
 *   [com.nexters.gamss.global.retry.ConflictRetry] 가 다시 실행해 재요청으로 끝낸다. 재시도가 없으면
 *   뒤늦은 요청이 유니크 위반으로 실패한다.
 * - 회수: [com.nexters.gamss.payment.repository.PaymentRepository.findByPlatformAndStoreTransactionKeyForUpdate]
 *   가 결제 행을 잠가 두 번째 요청이 앞선 회수를 보고 차이 0 으로 끝난다. 잠금이 없으면 둘 다 회수량 0 을
 *   읽어 같은 양을 두 번 회수한다.
 * - 계정 토큰: [com.nexters.gamss.payment.repository.PaymentAccountRepository.createIfAbsent] 가 한 문장으로
 *   만들고 잠금 읽기로 다시 읽는다. 조회 후 저장으로 되돌리면 첫 요청끼리 유니크 위반이 나고, 일반 조회로
 *   읽으면 앞서 고정된 스냅샷 때문에 방금 다른 요청이 만든 행을 보지 못한다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class PurchaseGrantConcurrencyIntegrationTest {
    @Autowired
    private lateinit var grantService: PurchaseGrantService

    @Autowired
    private lateinit var accountService: PaymentAccountService

    @Autowired
    private lateinit var walletReader: TokenWalletReader

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

    @BeforeEach
    fun setUp() {
        identityRepository.upsert(BUYER, SUBJECT)
    }

    @AfterEach
    fun cleanUp() {
        ledgerRepository.deleteAll()
        walletRepository.deleteAll()
        paymentRepository.deleteAll()
        accountRepository.deleteAll()
        identityRepository.deleteAll()
    }

    @Test
    fun `같은 거래를 동시에 여러 번 지급해도 한 번만 반영된다`() {
        val purchase = purchase(accountService.accountTokenOf(BUYER).value)

        val results = runConcurrently { grantService.grant(BUYER, purchase) }

        assertEquals(1, results.count { !it.replayed })
        assertEquals(1, results.map { it.paymentId }.distinct().size)
        assertEquals(1, paymentRepository.count())
        assertEquals(1, ledgerRepository.count())
        assertEquals(300_000, walletReader.balanceOf(BUYER))
    }

    @Test
    fun `같은 회수 목표가 동시에 와도 한 번만 회수된다`() {
        val purchase = purchase(accountService.accountTokenOf(BUYER).value)
        grantService.grant(BUYER, purchase)

        val results =
            runConcurrently { i ->
                grantService.applyRefund(PaymentPlatform.APPLE, purchase.storeTransactionKey, 300_000, "notification-$i")
            }

        assertEquals(1, results.count { it.changedTokens != 0L })
        assertEquals(2, ledgerRepository.count())
        assertEquals(0, walletReader.balanceOf(BUYER))
    }

    @Test
    fun `처음 계정 토큰을 동시에 요청해도 모두 같은 값을 받는다`() {
        val tokens = runConcurrently { accountService.accountTokenOf(BUYER) }

        assertEquals(1, tokens.distinct().size)
        assertEquals(1, accountRepository.count())
    }

    private fun <T : Any> runConcurrently(task: (Int) -> T): List<T> {
        val startLine = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val futures =
            (0 until THREADS).map { i ->
                executor.submit<T?> {
                    try {
                        startLine.await()
                        task(i)
                    } catch (t: Throwable) {
                        errors.add(t)
                        null
                    }
                }
            }
        try {
            val results = futures.mapNotNull { it.get(30, TimeUnit.SECONDS) }
            assertTrue(errors.isEmpty(), "동시 실행 중 예외 발생: $errors")
            return results
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun purchase(accountToken: String) =
        VerifiedPurchase(
            platform = PaymentPlatform.APPLE,
            storeTransactionKey = StoreTransactionKey("2000000123456789"),
            accountToken = accountToken,
            productId = "token_300000",
            quantity = 1,
            environment = PaymentEnvironment.PRODUCTION,
            purchasedAt = Instant.parse("2026-11-04T00:00:00Z"),
        )

    private companion object {
        const val THREADS = 8
        const val BUYER = 9_101L
        val SUBJECT = "d".repeat(64)
    }
}
