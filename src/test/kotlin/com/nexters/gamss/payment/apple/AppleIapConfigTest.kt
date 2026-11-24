package com.nexters.gamss.payment.apple

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppleIapConfigTest {
    private val config = AppleIapConfig()
    private val iap = AppleIapProperties("com.nexters.gamss", 6799644564L)

    @Test
    fun `리소스의 Apple 루트 인증서로 검증기를 만든다`() {
        assertIs<SignedAppleTransactionDecoder>(config.appleTransactionDecoder(iap))
        assertIs<SignedAppleNotificationDecoder>(config.appleNotificationDecoder(iap))
    }

    @Test
    fun `bundleId 나 앱 ID 가 올바르지 않으면 기동을 막는다`() {
        assertFailsWith<IllegalStateException> { config.appleTransactionDecoder(AppleIapProperties(" ", 6799644564L)) }
        assertFailsWith<IllegalStateException> { config.appleTransactionDecoder(AppleIapProperties("com.nexters.gamss", 0)) }
        assertFailsWith<IllegalStateException> { config.appleNotificationDecoder(AppleIapProperties(" ", 6799644564L)) }
    }

    @Test
    fun `Server API 키가 없으면 배치를 끄고 뜬다`() {
        assertIs<UnconfiguredAppleServerApiClient>(config.appleServerApiClient(iap, AppleServerApiProperties()))
    }

    @Test
    fun `Server API 키가 있으면 Apple 을 부르는 클라이언트를 만든다`() {
        val client = config.appleServerApiClient(iap, serverApi())

        assertIs<LibraryAppleServerApiClient>(client)
    }

    @Test
    fun `알림 기록 조회 범위는 Sandbox 30일, Production 180일 안이다`() {
        val sandbox = config.appleServerApiClient(iap, serverApi(environment = "SANDBOX")).maxLookback
        val production = config.appleServerApiClient(iap, serverApi(environment = "PRODUCTION")).maxLookback

        assertTrue(sandbox < Duration.ofDays(30) && sandbox > Duration.ofDays(29))
        assertTrue(production < Duration.ofDays(180) && production > Duration.ofDays(179))
    }

    /** 그대로 뜨면 배치가 매일 실패하는데, 그 상태는 놓친 알림이 생겨야 드러난다. */
    @Test
    fun `Server API 설정이 일부만 있거나 환경이 틀리면 기동을 막는다`() {
        assertFailsWith<IllegalStateException> { config.appleServerApiClient(iap, serverApi(keyId = "")) }
        assertFailsWith<IllegalStateException> { config.appleServerApiClient(iap, serverApi(environment = "")) }
        assertFailsWith<IllegalStateException> { config.appleServerApiClient(iap, serverApi(environment = "XCODE")) }
    }

    private fun serverApi(
        keyId: String = "ABC123DEFG",
        environment: String = "SANDBOX",
    ) = AppleServerApiProperties(
        signingKeyBase64 = Base64.getEncoder().encodeToString(signingKeyPem().toByteArray()),
        keyId = keyId,
        issuerId = "57246542-96fe-1a63-e053-0824d011072a",
        environment = environment,
    )

    /** App Store Connect 가 내려주는 `.p8` 과 같은 PKCS#8 EC 키. */
    private fun signingKeyPem(): String {
        val key =
            KeyPairGenerator
                .getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1")) }
                .generateKeyPair()
                .private
        return "-----BEGIN PRIVATE KEY-----\n${Base64.getMimeEncoder().encodeToString(key.encoded)}\n-----END PRIVATE KEY-----"
    }
}
