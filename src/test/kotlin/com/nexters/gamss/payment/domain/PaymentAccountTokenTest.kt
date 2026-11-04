package com.nexters.gamss.payment.domain

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaymentAccountTokenTest {
    @Test
    fun `스토어가 대문자로 돌려줘도 서버가 만든 소문자 토큰과 같다`() {
        val issued = PaymentAccountToken.of(UUID.fromString("3f2b8c1e-7a4d-4e6b-9c0a-1d2e3f4a5b6c"))

        assertEquals(issued, PaymentAccountToken.parseOrNull("3F2B8C1E-7A4D-4E6B-9C0A-1D2E3F4A5B6C"))
    }

    @Test
    fun `UUID 표준 표기가 아니면 null 이다`() {
        assertNull(PaymentAccountToken.parseOrNull(null))
        assertNull(PaymentAccountToken.parseOrNull(""))
        assertNull(PaymentAccountToken.parseOrNull("1-1-1-1-1"))
        assertNull(PaymentAccountToken.parseOrNull("3f2b8c1e7a4d4e6b9c0a1d2e3f4a5b6c"))
        assertNull(PaymentAccountToken.parseOrNull("zf2b8c1e-7a4d-4e6b-9c0a-1d2e3f4a5b6c"))
    }
}
