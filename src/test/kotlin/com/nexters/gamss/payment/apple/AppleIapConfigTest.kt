package com.nexters.gamss.payment.apple

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AppleIapConfigTest {
    private val config = AppleIapConfig()

    @Test
    fun `리소스의 Apple 루트 인증서로 검증기를 만든다`() {
        assertIs<SignedAppleTransactionDecoder>(config.appleTransactionDecoder(AppleIapProperties("com.nexters.gamss", 6799644564L)))
    }

    @Test
    fun `bundleId 나 앱 ID 가 올바르지 않으면 기동을 막는다`() {
        assertFailsWith<IllegalStateException> { config.appleTransactionDecoder(AppleIapProperties(" ", 6799644564L)) }
        assertFailsWith<IllegalStateException> { config.appleTransactionDecoder(AppleIapProperties("com.nexters.gamss", 0)) }
    }
}
