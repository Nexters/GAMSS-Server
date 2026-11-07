package com.nexters.gamss.tokenlimit.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PaidTokenShareTest {
    @Test
    fun `한도 안이면 유료 몫이 없다`() {
        assertEquals(0, PaidTokenShare.of(newUsed = 50_000, delta = 3_000, limit = LIMIT))
    }

    @Test
    fun `한도에 정확히 닿기만 하면 유료 몫이 없다`() {
        assertEquals(0, PaidTokenShare.of(newUsed = LIMIT, delta = 3_000, limit = LIMIT))
    }

    @Test
    fun `경계를 걸치면 넘긴 만큼만 유료 몫이다`() {
        assertEquals(2_000, PaidTokenShare.of(newUsed = 102_000, delta = 3_000, limit = LIMIT))
    }

    @Test
    fun `이미 한도를 넘겼으면 전부 유료 몫이다`() {
        assertEquals(3_000, PaidTokenShare.of(newUsed = 105_000, delta = 3_000, limit = LIMIT))
    }

    @Test
    fun `일일 토큰이 0 이면 전부 유료 몫이다`() {
        assertEquals(3_000, PaidTokenShare.of(newUsed = 3_000, delta = 3_000, limit = 0))
    }

    /** 이미 깎은 유료 토큰은 되돌리지 않고, 그 뒤 생성부터 새 한도로 계산한다. */
    @Test
    fun `한도가 사용량 아래로 낮아지면 그 뒤 생성은 전부 유료 몫이다`() {
        assertEquals(3_000, PaidTokenShare.of(newUsed = 83_000, delta = 3_000, limit = 50_000))
    }

    private companion object {
        const val LIMIT = 100_000L
    }
}
