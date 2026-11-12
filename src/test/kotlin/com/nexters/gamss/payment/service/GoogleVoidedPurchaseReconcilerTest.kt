package com.nexters.gamss.payment.service

import com.nexters.gamss.payment.google.GoogleVoidedPurchase
import com.nexters.gamss.payment.repository.GoogleRefundRecordRepository
import com.nexters.gamss.support.FakeGooglePlayClient
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * '환불 대사 중단' 경보가 보는 게이지를 고정한다. 게이지가 틀리면 경보가 울려야 할 때 울리지 않거나(배포마다 나이가 0 으로
 * 돌아감, 실패해도 갱신됨), 울리지 않아야 할 때 울린다(배치가 돌지 않는 환경에 게이지가 있음).
 */
class GoogleVoidedPurchaseReconcilerTest {
    private val google = FakeGooglePlayClient()
    private val reclaimer = mockk<GoogleRefundReclaimer>()
    private val recordRepository = mockk<GoogleRefundRecordRepository>(relaxUnitFun = true)
    private val meterRegistry = SimpleMeterRegistry()

    @Test
    fun `기동 직후 저장된 마지막 성공 시각으로 게이지를 맞춘다`() {
        val stored = Instant.parse("2026-11-01T04:10:00Z")
        every { recordRepository.lastSucceededAt(GoogleVoidedPurchaseReconciler.JOB_NAME) } returns stored
        val reconciler = reconciler()

        reconciler.loadLastSuccess()

        assertEquals(stored.epochSecond.toDouble(), gauge())
    }

    @Test
    fun `저장된 시각을 읽지 못해도 기동을 막지 않고 기동 시각을 둔다`() {
        every { recordRepository.lastSucceededAt(GoogleVoidedPurchaseReconciler.JOB_NAME) } throws IllegalStateException("db down")
        val reconciler = reconciler()
        val bootTime = gauge()

        reconciler.loadLastSuccess()

        assertEquals(bootTime, gauge())
    }

    /** 게이지가 있으면 기동 시각에서 멈춰 배치가 돌지 않는 환경에서도 '대사 중단' 경보가 울린다. */
    @Test
    fun `자격증명이 없는 환경에는 게이지를 두지 않는다`() {
        google.configured = false

        reconciler()

        assertNull(meterRegistry.find(GoogleVoidedPurchaseReconciler.LAST_SUCCESS_METRIC).gauge())
        assertNotNull(meterRegistry.find(GoogleVoidedPurchaseReconciler.FAILURE_METRIC).counter())
    }

    /** 실패한 항목이 있는데 게이지가 갱신되면 매일 실패하는 항목이 있어도 경보가 울리지 않는다. */
    @Test
    fun `항목이 실패한 회차는 게이지와 체크포인트를 갱신하지 않는다`() {
        every { recordRepository.lastSucceededAt(any()) } returns null
        every { recordRepository.isVoidedPurchaseHandled(any(), any()) } returns false
        every { reclaimer.reclaim(any(), any(), any()) } throws IllegalStateException("일시 오류")
        google.voidedPages = listOf(listOf(voided()))
        val reconciler = reconciler()

        assertFailsWith<IllegalStateException> { reconciler.reconcile(NOW) }

        assertNotEquals(NOW.epochSecond.toDouble(), gauge())
        verify(exactly = 0) { recordRepository.saveLastSucceededAt(any(), any()) }
    }

    @Test
    fun `성공한 회차는 게이지를 그 시각으로 갱신한다`() {
        every { recordRepository.lastSucceededAt(any()) } returns null
        val reconciler = reconciler()

        reconciler.reconcile(NOW)

        assertEquals(NOW.epochSecond.toDouble(), gauge())
        verify(exactly = 1) { recordRepository.saveLastSucceededAt(GoogleVoidedPurchaseReconciler.JOB_NAME, NOW) }
    }

    private fun reconciler() = GoogleVoidedPurchaseReconciler(google, reclaimer, recordRepository, meterRegistry)

    private fun gauge(): Double = meterRegistry.get(GoogleVoidedPurchaseReconciler.LAST_SUCCESS_METRIC).gauge().value()

    private fun voided() =
        GoogleVoidedPurchase(
            purchaseToken = "google-purchase-token-for-reconciler-test",
            orderId = "GPA.RECONCILER-0001",
            voidedTime = NOW.minusSeconds(3600),
            voidedSource = 0,
            voidedReason = 0,
            voidedQuantity = null,
        )

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-12T00:00:00Z")
    }
}
