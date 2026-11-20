package com.nexters.gamss.payment.service

import com.nexters.gamss.payment.repository.PaymentJobCheckpointRepository
import com.nexters.gamss.support.FakeAppleServerApiClient
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** '알림 기록 대사 중단' 경보가 보는 게이지를 고정한다. 이유는 GoogleVoidedPurchaseReconcilerTest 와 같다. */
class AppleNotificationHistoryReconcilerTest {
    private val client = FakeAppleServerApiClient()
    private val notificationService = mockk<AppleNotificationService>()
    private val checkpointRepository = mockk<PaymentJobCheckpointRepository>(relaxUnitFun = true)
    private val meterRegistry = SimpleMeterRegistry()

    @Test
    fun `기동 직후 저장된 마지막 성공 시각으로 게이지를 맞춘다`() {
        val stored = Instant.parse("2026-11-01T04:20:00Z")
        every { checkpointRepository.lastSucceededAt(AppleNotificationHistoryReconciler.JOB_NAME) } returns stored
        val reconciler = reconciler()

        reconciler.loadLastSuccess()

        assertEquals(stored.epochSecond.toDouble(), gauge())
    }

    @Test
    fun `키가 없는 환경에는 게이지를 두지 않고 배치도 돌지 않는다`() {
        client.configured = false
        val reconciler = reconciler()

        reconciler.reconcile(NOW)

        assertNull(meterRegistry.find(AppleNotificationHistoryReconciler.LAST_SUCCESS_METRIC).gauge())
        assertNotNull(meterRegistry.find(AppleNotificationHistoryReconciler.FAILURE_METRIC).counter())
        verify(exactly = 0) { checkpointRepository.saveLastSucceededAt(any(), any()) }
    }

    @Test
    fun `성공한 회차는 게이지를 그 시각으로 갱신한다`() {
        every { checkpointRepository.lastSucceededAt(any()) } returns null
        every { notificationService.handle(any()) } returns AppleNotificationOutcome.ACK
        client.pages = listOf(listOf("payload"))
        val reconciler = reconciler()

        reconciler.reconcile(NOW)

        assertEquals(NOW.epochSecond.toDouble(), gauge())
        verify(exactly = 1) { checkpointRepository.saveLastSucceededAt(AppleNotificationHistoryReconciler.JOB_NAME, NOW) }
    }

    @Test
    fun `실패한 회차는 실패 카운터를 올린다`() {
        every { checkpointRepository.lastSucceededAt(any()) } returns null
        client.unavailable = true
        val reconciler = reconciler()

        reconciler.reconcile()

        assertEquals(1.0, meterRegistry.counter(AppleNotificationHistoryReconciler.FAILURE_METRIC).count())
    }

    private fun reconciler() = AppleNotificationHistoryReconciler(client, notificationService, checkpointRepository, meterRegistry)

    private fun gauge(): Double = meterRegistry.get(AppleNotificationHistoryReconciler.LAST_SUCCESS_METRIC).gauge().value()

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-12T00:00:00Z")
    }
}
