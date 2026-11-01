package com.nexters.gamss.wallet.service

import com.nexters.gamss.global.retry.ConflictRetry
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.wallet.domain.LedgerRefId
import com.nexters.gamss.wallet.domain.TokenAmount
import com.nexters.gamss.wallet.domain.TokenLedgerType
import com.nexters.gamss.wallet.domain.TokenWalletChange
import com.nexters.gamss.wallet.repository.TokenLedgerRepository
import com.nexters.gamss.wallet.repository.TokenWalletRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.support.TransactionTemplate
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 실제 커밋끼리 경합해야 하므로 테스트 트랜잭션 롤백을 쓰지 않고 직접 정리한다. */
@SpringBootTest
@Import(TestcontainersConfig::class)
class TokenWalletConcurrencyIntegrationTest {
    @Autowired
    private lateinit var walletService: TokenWalletService

    @Autowired
    private lateinit var conflictRetry: ConflictRetry

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var walletRepository: TokenWalletRepository

    @Autowired
    private lateinit var ledgerRepository: TokenLedgerRepository

    @AfterEach
    fun cleanUp() {
        ledgerRepository.deleteAll()
        walletRepository.deleteAll()
    }

    @Test
    fun `지갑이 없는 주체에 동시에 증감해도 잔액이 증감 합과 원장 합과 같다`() {
        val changes =
            (1..THREADS).map { i ->
                if (i % 3 == 0) {
                    change(TokenLedgerType.ADMIN_REVOKE, 700, "admin:revoke:$i")
                } else {
                    change(TokenLedgerType.PURCHASE, 1_000, "purchase:$i")
                }
            }

        runConcurrently(changes)

        val expected = changes.sumOf { it.type.signed(it.amount) }
        val ledger = ledgerRepository.findAll().sortedBy { it.id }
        assertEquals(expected, walletRepository.findById(SUBJECT).get().balance)
        assertEquals(expected, ledger.sumOf { it.amount })
        assertBalanceChain(ledger.map { it.amount to it.balanceAfter })
    }

    @Test
    fun `같은 멱등 키가 동시에 와도 한 번만 반영된다`() {
        val results = runConcurrently(List(THREADS) { change(TokenLedgerType.PURCHASE, 3_000, "purchase:1") })

        assertEquals(1, results.count { !it.replayed })
        assertEquals(1, ledgerRepository.count())
        assertEquals(3_000, walletRepository.findById(SUBJECT).get().balance)
    }

    @Test
    fun `동시 사용은 잔액까지만 깎고 0 아래로 내려가지 않는다`() {
        walletService.apply(change(TokenLedgerType.PURCHASE, 1_000, "purchase:1"))

        runConcurrently(List(THREADS) { change(TokenLedgerType.CONSUME, 300, null) })

        assertEquals(0, walletRepository.findById(SUBJECT).get().balance)
        assertEquals(-1_000, ledgerRepository.findAll().filter { it.type == TokenLedgerType.CONSUME }.sumOf { it.amount })
    }

    /** 바깥 트랜잭션이 먼저 읽어 스냅샷이 잡히면, 잠금 뒤 조회로도 앞선 커밋을 보지 못하고 유니크 제약에 걸린다. */
    @Test
    fun `바깥 트랜잭션이 이미 읽은 뒤 같은 멱등 키가 커밋되면 재시도할 수 있는 충돌로 알린다`() {
        val purchase = change(TokenLedgerType.PURCHASE, 3_000, "purchase:1")
        val other = Executors.newSingleThreadExecutor()

        try {
            assertThrows<TokenLedgerConflictException> {
                transactionTemplate.executeWithoutResult {
                    ledgerRepository.count()
                    other.submit { walletService.apply(purchase) }.get(30, TimeUnit.SECONDS)
                    walletService.apply(purchase)
                }
            }
        } finally {
            other.shutdownNow()
        }

        val retried = conflictRetry.execute { transactionTemplate.execute { walletService.apply(purchase) }!! }
        assertTrue(retried.replayed)
        assertEquals(1, ledgerRepository.count())
    }

    @Test
    fun `같은 트랜잭션에서 지갑을 먼저 읽어 둬도 그 사이 커밋된 증감을 덮어쓰지 않는다`() {
        walletService.apply(change(TokenLedgerType.PURCHASE, 1_000, "purchase:0"))
        val other = Executors.newSingleThreadExecutor()

        try {
            transactionTemplate.executeWithoutResult {
                walletRepository.findById(SUBJECT).get()
                other.submit { walletService.apply(change(TokenLedgerType.PURCHASE, 100, "purchase:1")) }.get(30, TimeUnit.SECONDS)
                walletService.apply(change(TokenLedgerType.PURCHASE, 1, "purchase:2"))
            }
        } finally {
            other.shutdownNow()
        }

        assertEquals(1_101, walletRepository.findById(SUBJECT).get().balance)
    }

    private fun runConcurrently(changes: List<TokenWalletChange>): List<TokenWalletResult> {
        val startLine = CyclicBarrier(changes.size)
        val executor = Executors.newFixedThreadPool(changes.size)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val futures =
            changes.map { change ->
                executor.submit<TokenWalletResult?> {
                    try {
                        startLine.await()
                        conflictRetry.execute { walletService.apply(change) }
                    } catch (t: Throwable) {
                        errors.add(t)
                        null
                    }
                }
            }
        try {
            val results = futures.mapNotNull { it.get(30, TimeUnit.SECONDS) }
            assertTrue(errors.isEmpty(), "동시 반영 중 예외 발생: $errors")
            return results
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun assertBalanceChain(entries: List<Pair<Long, Long>>) {
        entries.fold(0L) { before, (amount, after) ->
            assertEquals(before + amount, after, "원장의 반영 후 잔액이 id 순서로 이어지지 않는다")
            after
        }
    }

    private fun change(
        type: TokenLedgerType,
        amount: Long,
        refId: String?,
    ) = TokenWalletChange(SUBJECT, type, TokenAmount(amount), refId?.let(::LedgerRefId))

    private companion object {
        const val THREADS = 12
        val SUBJECT = "a".repeat(64)
    }
}
