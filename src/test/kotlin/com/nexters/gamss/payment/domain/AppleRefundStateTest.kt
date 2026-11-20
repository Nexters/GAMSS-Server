package com.nexters.gamss.payment.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppleRefundStateTest {
    @Test
    fun `환불되지 않았으면 회수 목표는 0 이다`() {
        assertEquals(0, AppleRefundState.NOT_REFUNDED.targetFor(300_000))
    }

    @Test
    fun `비율이 없으면 전액 회수한다`() {
        assertEquals(300_000, AppleRefundState.refunded(null).targetFor(300_000))
    }

    @Test
    fun `부분 환불은 milliunits 비율만큼 회수하고 내림한다`() {
        assertEquals(150_000, AppleRefundState.refunded(50_000).targetFor(300_000))
        assertEquals(300_000, AppleRefundState.refunded(AppleRefundState.FULL).targetFor(300_000))
        assertEquals(33, AppleRefundState.refunded(33_333).targetFor(100))
    }

    @Test
    fun `비율이 범위를 벗어나거나 환불되지 않은 상태에 비율이 있으면 거부한다`() {
        assertFailsWith<IllegalArgumentException> { AppleRefundState.refunded(AppleRefundState.FULL + 1) }
        assertFailsWith<IllegalArgumentException> { AppleRefundState.refunded(-1) }
        assertFailsWith<IllegalArgumentException> { AppleRefundState(refunded = false, revocationPercentage = 50_000) }
    }
}
