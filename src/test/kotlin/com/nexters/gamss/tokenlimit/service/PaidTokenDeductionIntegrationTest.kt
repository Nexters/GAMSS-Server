package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.member.domain.MemberSocialIdentity
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import com.nexters.gamss.wallet.domain.LedgerRefId
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 일일 토큰을 다 쓴 뒤 유료 토큰에서 이어서 깎는 흐름(#239)을 실제 잠금과 롤백으로 확인한다.
 *
 * 상한을 켜지 않으면 차감 자체가 일어나지 않아 켠다. 커밋끼리 경합하고 롤백 뒤 재적립까지 봐야 하므로
 * 테스트 트랜잭션 롤백을 쓰지 않고 직접 정리한다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
@TestPropertySource(properties = ["gamss.token-limit.enabled=true"])
class PaidTokenDeductionIntegrationTest {
    @Autowired
    private lateinit var recorder: TokenQuotaRecorder

    @Autowired
    private lateinit var limitService: DailyTokenLimitService

    @Autowired
    private lateinit var tokenPolicyService: TokenPolicyService

    @Autowired
    private lateinit var walletService: TokenWalletService

    @Autowired
    private lateinit var walletReader: TokenWalletReader

    @Autowired
    private lateinit var walletRepository: TokenWalletRepository

    @Autowired
    private lateinit var ledgerRepository: TokenLedgerRepository

    @Autowired
    private lateinit var usageRepository: TokenQuotaUsageRepository

    @Autowired
    private lateinit var identityRepository: MemberSocialIdentityRepository

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private var limit = 0

    @BeforeEach
    fun setUp() {
        limit = tokenPolicyService.current().dailyTokenLimit.toInt()
        identityRepository.save(MemberSocialIdentity(MEMBER_ID, SUBJECT))
    }

    @AfterEach
    fun cleanUp() {
        ledgerRepository.deleteAll()
        walletRepository.deleteAll()
        usageRepository.deleteAll()
        identityRepository.deleteAll()
    }

    @Test
    fun `일일 토큰이 남아 있는 동안에는 유료 잔액이 그대로다`() {
        purchase(10_000)

        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, limit - 1)

        assertEquals(10_000, walletReader.balanceOf(MEMBER_ID))
    }

    @Test
    fun `한도 경계를 걸친 1건은 넘긴 만큼만 깎인다`() {
        purchase(10_000)
        useDailyTokens(limit - 1_000)

        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 3_000)

        assertEquals(8_000, walletReader.balanceOf(MEMBER_ID))
        assertEquals(listOf(-2_000L), consumed())
        assertEquals(limit + 2_000L, limitService.usageFor(MEMBER_ID).usedTokens)
    }

    @Test
    fun `한도를 이미 넘긴 뒤의 생성은 전부 깎인다`() {
        purchase(10_000)
        useDailyTokens(limit)

        recorder.recordDelivered(GenerationType.REPLY, MEMBER_ID, 3_000)

        assertEquals(7_000, walletReader.balanceOf(MEMBER_ID))
    }

    @Test
    fun `잔액보다 많이 쓰면 0 에서 멈추고 원장에는 실제로 깎은 양만 남는다`() {
        purchase(500)
        useDailyTokens(limit)

        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 3_000)

        assertEquals(0, walletReader.balanceOf(MEMBER_ID))
        assertEquals(listOf(-500L), consumed())
    }

    @Test
    fun `산 적 없는 주체는 적립만 되고 지갑이 생기지 않는다`() {
        useDailyTokens(limit)

        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 3_000)

        assertEquals(limit + 3_000L, limitService.usageFor(MEMBER_ID).usedTokens)
        assertEquals(0, walletRepository.count())
    }

    @Test
    fun `환불 회수로 잔액이 음수면 더 깎지 않는다`() {
        revoke(1_000)
        useDailyTokens(limit)

        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 3_000)

        assertEquals(-1_000, walletReader.balanceOf(MEMBER_ID))
        assertEquals(emptyList(), consumed())
    }

    @Test
    fun `응답을 받지 못한 생성은 일일 사용량만 쌓이고 유료 잔액은 그대로다`() {
        purchase(10_000)
        useDailyTokens(limit)

        recorder.record(GenerationType.COMMENT, MEMBER_ID, 3_000)

        assertEquals(10_000, walletReader.balanceOf(MEMBER_ID))
        assertEquals(limit + 3_000L, limitService.usageFor(MEMBER_ID).usedTokens)
    }

    /** 앞서 못 받은 생성으로 쌓인 사용량이 다음 생성의 차감에 얹히면 안 된다. */
    @Test
    fun `응답을 받지 못한 생성의 초과분은 다음 생성에서 깎이지 않는다`() {
        purchase(10_000)
        useDailyTokens(limit)
        recorder.record(GenerationType.COMMENT, MEMBER_ID, 3_000)

        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 1_000)

        assertEquals(9_000, walletReader.balanceOf(MEMBER_ID))
    }

    @Test
    fun `한도에 합산하지 않는 종류는 유료 잔액을 깎지 않는다`() {
        purchase(10_000)
        useDailyTokens(limit)

        GenerationType.entries.filterNot { it.countsTowardQuota }.forEach { type ->
            recorder.recordDelivered(type, MEMBER_ID, 3_000)
        }

        assertEquals(10_000, walletReader.balanceOf(MEMBER_ID))
    }

    @Test
    fun `같은 회원이 동시에 생성해도 유료 차감 합이 식과 정확히 맞는다`() {
        purchase(1_000_000)
        useDailyTokens(limit - 1_000)
        val before = settleErrorCount()

        runConcurrently { recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 500) }

        val expected = THREADS * 500L - 1_000
        assertEquals(1_000_000 - expected, walletReader.balanceOf(MEMBER_ID))
        assertEquals(-expected, consumed().sum())
        assertEquals(limit - 1_000 + THREADS * 500L, limitService.usageFor(MEMBER_ID).usedTokens)
        assertEquals(before, settleErrorCount())
    }

    @Test
    fun `일일 토큰과 유료 잔액이 모두 없으면 생성할 수 없다`() {
        useDailyTokens(limit)

        assertFalse(limitService.isWithinLimit(MEMBER_ID))
    }

    @Test
    fun `유료 잔액이 있으면 일일 한도를 넘겨도 생성할 수 있다`() {
        purchase(1)
        useDailyTokens(limit)

        assertTrue(limitService.isWithinLimit(MEMBER_ID))
    }

    @Test
    fun `잔액이 음수여도 일일 토큰이 남아 있으면 생성할 수 있다`() {
        revoke(1_000)

        assertTrue(limitService.isWithinLimit(MEMBER_ID))
    }

    @Test
    fun `잔액이 음수이고 일일 토큰을 다 쓰면 생성할 수 없다`() {
        revoke(1_000)
        useDailyTokens(limit)

        assertFalse(limitService.isWithinLimit(MEMBER_ID))
    }

    /**
     * 주체 키 형식이 틀리면 트랜잭션이 적립 뒤 지갑 단계에서 실패한다. 적립이 롤백되고 다시 쌓이므로,
     * 일일 사용량이 정확히 한 번만 늘어야 롤백과 재적립이 모두 일어난 것이다.
     */
    @Test
    fun `유료 차감이 실패해도 일일 사용량은 한 번만 적립되고 카운터가 오른다`() {
        identityRepository.save(MemberSocialIdentity(BROKEN_MEMBER_ID, "z".repeat(64)))
        recorder.record(GenerationType.COMMENT, BROKEN_MEMBER_ID, limit)
        val before = settleErrorCount()

        recorder.recordDelivered(GenerationType.COMMENT, BROKEN_MEMBER_ID, 3_000)

        assertEquals(limit + 3_000L, limitService.usageFor(BROKEN_MEMBER_ID).usedTokens)
        assertEquals(before + 1, settleErrorCount())
    }

    private fun useDailyTokens(tokens: Int) = recorder.record(GenerationType.COMMENT, MEMBER_ID, tokens)

    private fun purchase(tokens: Long) {
        walletService.apply(TokenWalletChange(SUBJECT, TokenLedgerType.PURCHASE, TokenAmount(tokens), LedgerRefId("purchase:1")))
    }

    private fun revoke(tokens: Long) {
        walletService.apply(TokenWalletChange(SUBJECT, TokenLedgerType.ADMIN_REVOKE, TokenAmount(tokens), LedgerRefId("revoke:1")))
    }

    private fun consumed(): List<Long> = ledgerRepository.findAll().filter { it.type == TokenLedgerType.CONSUME }.map { it.amount }

    private fun settleErrorCount(): Double =
        meterRegistry
            .get("gamss.tokenlimit.quota.record.failure")
            .tag("reason", "settle_error")
            .counter()
            .count()

    private fun runConcurrently(task: () -> Unit) {
        val startLine = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        repeat(THREADS) {
            executor.submit {
                try {
                    startLine.await()
                    task()
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }
        executor.shutdown()
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS), "동시 실행이 시간 안에 끝나지 않았다")
        assertTrue(errors.isEmpty(), "동시 실행 중 예외 발생: $errors")
    }

    private companion object {
        const val THREADS = 8
        const val MEMBER_ID = 9_100_001L
        const val BROKEN_MEMBER_ID = 9_100_002L
        val SUBJECT = "a".repeat(64)
    }
}
