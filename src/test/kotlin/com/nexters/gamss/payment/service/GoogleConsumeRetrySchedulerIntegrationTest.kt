package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.domain.Payment
import com.nexters.gamss.payment.domain.PaymentEnvironment
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.google.GoogleConsumptionState
import com.nexters.gamss.payment.google.GooglePurchaseState
import com.nexters.gamss.payment.repository.PaymentRepository
import com.nexters.gamss.support.FakeGooglePlayClient
import com.nexters.gamss.support.FakeGooglePlayClient.Companion.purchase
import com.nexters.gamss.support.FakeGooglePlayClientConfig
import com.nexters.gamss.support.TestcontainersConfig
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 결제 행을 직접 심고 [GoogleConsumeRetryScheduler.retry] 를 시각과 함께 부른다.
 *
 * 이 컨텍스트는 캐시돼 다른 테스트 클래스와 함께 쓰이므로, 뜬 지 10분이 지나면 주기 실행이 백그라운드에서
 * 돌 수 있다. 그래도 끼어들지 않는 이유는 테스트가 심은 행의 생성 시각이 실제 현재 시각이라 주기 실행의
 * 막 만든 행 건너뛰기([GoogleConsumeRetryScheduler.FRESH_ROW_GRACE])에 걸리기 때문이다. 생성 시각을
 * 과거로 옮겨 심는 테스트를 추가하면 이 전제가 깨진다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class, FakeGooglePlayClientConfig::class)
class GoogleConsumeRetrySchedulerIntegrationTest {
    @Autowired
    private lateinit var scheduler: GoogleConsumeRetryScheduler

    @Autowired
    private lateinit var google: FakeGooglePlayClient

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    /** 행이 만들어진 시각은 지금이므로, 막 만든 행 건너뛰기를 지나도록 회차 시각을 그 뒤로 둔다. */
    private val now: Instant = Instant.now().plus(Duration.ofMinutes(10))

    @BeforeEach
    fun setUp() {
        google.reset()
    }

    @AfterEach
    fun cleanUp() {
        paymentRepository.deleteAll()
    }

    @Test
    fun `consume 이 실패한 결제를 다시 consume 하고 기록을 채운다`() {
        val payment = savedPayment("t1", purchasedAgo = Duration.ofHours(1))
        google.stub("t1", purchase(accountToken = null))

        scheduler.retry(now)

        assertEquals(listOf("token_300000" to "t1"), google.consumeCalls)
        assertEquals(listOf(false), google.consumeInTransaction)
        assertNotNull(consumedAtOf(payment))
    }

    @Test
    fun `이미 consume 된 구매는 consume 을 부르지 않고 기록만 채운다`() {
        val payment = savedPayment("t1", purchasedAgo = Duration.ofHours(1))
        google.stub("t1", purchase(accountToken = null, consumption = GoogleConsumptionState.CONSUMED))

        scheduler.retry(now)

        assertTrue(google.consumeCalls.isEmpty())
        assertNotNull(consumedAtOf(payment))
    }

    @Test
    fun `취소된 구매는 consume 하지 않는다`() {
        val payment = savedPayment("t1", purchasedAgo = Duration.ofHours(1))
        google.stub("t1", purchase(accountToken = null, state = GooglePurchaseState.CANCELLED))

        scheduler.retry(now)

        assertTrue(google.consumeCalls.isEmpty())
        assertNull(consumedAtOf(payment))
    }

    @Test
    fun `구매 후 3일이 지난 결제는 대상에서 뺀다`() {
        savedPayment("old", purchasedAgo = Duration.ofDays(3).plusMinutes(1))
        google.stub("old", purchase(accountToken = null))

        scheduler.retry(now)

        assertTrue(google.consumeCalls.isEmpty())
    }

    @Test
    fun `막 만든 결제는 앱 요청 경로가 consume 중일 수 있어 건너뛴다`() {
        savedPayment("fresh", purchasedAgo = Duration.ofMinutes(1))
        google.stub("fresh", purchase(accountToken = null))

        scheduler.retry(Instant.now())

        assertTrue(google.consumeCalls.isEmpty())
    }

    @Test
    fun `한 건이 실패해도 다음 결제는 처리한다`() {
        savedPayment("broken", purchasedAgo = Duration.ofHours(2))
        val next = savedPayment("t2", purchasedAgo = Duration.ofHours(1))
        google.stub("t2", purchase(accountToken = null))

        scheduler.retry(now)

        assertNotNull(consumedAtOf(next))
    }

    @Test
    fun `48시간이 지나도 consume 하지 못하면 적체 카운터를 올린다`() {
        savedPayment("t1", purchasedAgo = Duration.ofHours(49))
        savedPayment("sandbox", purchasedAgo = Duration.ofHours(49), environment = PaymentEnvironment.SANDBOX)
        savedPayment("recent", purchasedAgo = Duration.ofHours(1))
        listOf("t1", "sandbox", "recent").forEach { google.stub(it, purchase(accountToken = null)) }
        google.consumeFailure = BusinessException(ErrorCode.PURCHASE_INVALID)
        val before = staleCount()

        scheduler.retry(now)

        assertEquals(1.0, staleCount() - before)
    }

    @Test
    fun `Google 과 통신할 수 없으면 회차를 멈추고 남은 결제의 48시간 경보는 센다`() {
        savedPayment("t1", purchasedAgo = Duration.ofHours(50))
        savedPayment("t2", purchasedAgo = Duration.ofHours(49))
        savedPayment("t3", purchasedAgo = Duration.ofHours(1))
        google.getPurchaseUnavailable = true
        val before = staleCount()

        scheduler.retry(now)

        assertEquals(1, google.getPurchaseCalls)
        assertEquals(2.0, staleCount() - before)
    }

    @Test
    fun `consume 단계에서 Google 과 통신할 수 없어도 회차를 멈춘다`() {
        savedPayment("t1", purchasedAgo = Duration.ofHours(2))
        savedPayment("t2", purchasedAgo = Duration.ofHours(1))
        listOf("t1", "t2").forEach { google.stub(it, purchase(accountToken = null)) }
        google.consumeFailure = BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)

        scheduler.retry(now)

        assertEquals(listOf("token_300000" to "t1"), google.consumeCalls)
        assertEquals(1, google.getPurchaseCalls)
    }

    @Test
    fun `Google 이 consume 을 거부한 결제는 그 결제만 넘기고 회차를 이어 간다`() {
        savedPayment("t1", purchasedAgo = Duration.ofHours(2))
        savedPayment("t2", purchasedAgo = Duration.ofHours(1))
        listOf("t1", "t2").forEach { google.stub(it, purchase(accountToken = null)) }
        google.consumeFailure = BusinessException(ErrorCode.PURCHASE_INVALID)

        scheduler.retry(now)

        assertEquals(listOf("t1", "t2"), google.consumeCalls.map { it.second })
    }

    @Test
    fun `회차 시간 상한을 넘으면 남은 결제는 다음 회차로 넘긴다`() {
        val first = savedPayment("t1", purchasedAgo = Duration.ofHours(2))
        val second = savedPayment("t2", purchasedAgo = Duration.ofHours(1))
        listOf("t1", "t2").forEach { google.stub(it, purchase(accountToken = null)) }

        scheduler.retry(now, roundBudget = Duration.ZERO)

        assertEquals(1, google.getPurchaseCalls)
        assertNotNull(consumedAtOf(first))
        assertNull(consumedAtOf(second))
    }

    @Test
    fun `자격증명이 없는 환경에서는 아무것도 하지 않는다`() {
        savedPayment("t1", purchasedAgo = Duration.ofHours(1))
        google.stub("t1", purchase(accountToken = null))
        google.configured = false

        scheduler.retry(now)

        assertEquals(0, google.getPurchaseCalls)
    }

    @Test
    fun `먼저 기록된 consume 시각은 덮지 않는다`() {
        val payment = savedPayment("t1", purchasedAgo = Duration.ofHours(1))
        val first = Instant.parse("2026-11-01T00:00:00Z")
        paymentRepository.markConsumed(payment.id, first)

        assertEquals(0, paymentRepository.markConsumed(payment.id, Instant.parse("2026-11-02T00:00:00Z")))

        assertEquals(first, consumedAtOf(payment))
    }

    /**
     * 스케줄러가 결제를 읽은 뒤 기록하기 전에 환불 회수가 커밋되는 경우다. consume 기록을 엔티티 변경 감지로
     * 바꾸면, 읽어 둔 옛 회수량(0)이 행 전체와 함께 저장돼 커밋된 회수량을 되돌린다.
     */
    @Test
    fun `consume 을 기록해도 그 사이 커밋된 회수량을 되돌리지 않는다`() {
        val payment = savedPayment("t1", purchasedAgo = Duration.ofHours(1))
        google.stub("t1", purchase(accountToken = null))
        google.afterGetPurchase = {
            transactionTemplate.executeWithoutResult {
                paymentRepository.findById(payment.id).get().refundTo(100)
            }
        }

        scheduler.retry(now)

        val saved = paymentRepository.findById(payment.id).get()
        assertNotNull(saved.consumedAt)
        assertEquals(100, saved.refundedTokens)
    }

    private fun savedPayment(
        token: String,
        purchasedAgo: Duration,
        environment: PaymentEnvironment = PaymentEnvironment.PRODUCTION,
    ): Payment =
        paymentRepository.saveAndFlush(
            Payment(
                subjectKey = "f".repeat(64),
                platform = PaymentPlatform.GOOGLE,
                storeTransactionKey = StoreTransactionKey.ofGooglePurchaseToken(token).value,
                storePurchaseToken = token,
                storeOrderId = "GPA.$token",
                productId = "token_300000",
                quantity = 1,
                grantedTokens = 300_000,
                environment = environment,
                purchasedAt = now.minus(purchasedAgo),
            ),
        )

    private fun consumedAtOf(payment: Payment): Instant? = paymentRepository.findById(payment.id).get().consumedAt

    private fun staleCount(): Double = meterRegistry.counter(GoogleConsumeRetryScheduler.STALE_METRIC).count()
}
