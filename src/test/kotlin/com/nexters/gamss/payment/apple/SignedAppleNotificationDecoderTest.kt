package com.nexters.gamss.payment.apple

import com.nexters.gamss.payment.apple.AppleJwsFixture.Companion.APP_APPLE_ID
import com.nexters.gamss.payment.apple.AppleJwsFixture.Companion.BUNDLE_ID
import com.nexters.gamss.payment.apple.AppleJwsFixture.Companion.SIGNED_AT
import com.nexters.gamss.payment.domain.PaymentEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 실제 라이브러리로 알림 서명을 검증한다. Apple 과 같은 모양의 자체 인증서 체인([AppleJwsFixture])을 쓴다. */
class SignedAppleNotificationDecoderTest {
    private val fixture = AppleJwsFixture()
    private val decoder = SignedAppleNotificationDecoder(listOf(fixture.root.encoded), BUNDLE_ID, APP_APPLE_ID)

    @Test
    fun `Production 알림을 검증하고 푼다`() {
        val notification = decoder.decode(fixture.signedTransaction(payload()))

        assertEquals("uuid-1", notification.notificationUuid)
        assertEquals("REFUND", notification.type)
        assertEquals(PaymentEnvironment.PRODUCTION, notification.environment)
        assertEquals("signed-transaction", notification.signedTransactionInfo)
        assertEquals(SIGNED_AT, notification.signedDate)
    }

    /** Sandbox 알림에는 appAppleId 가 없다. Production 검증기가 이를 앱 식별자 불일치로 거부해도 Sandbox 로 넘어가야 한다. */
    @Test
    fun `appAppleId 가 없는 Sandbox 알림도 검증하고 푼다`() {
        val notification = decoder.decode(fixture.signedTransaction(payload(environment = "Sandbox", appAppleId = null)))

        assertEquals(PaymentEnvironment.SANDBOX, notification.environment)
    }

    @Test
    fun `다른 루트로 서명한 알림은 거부한다`() {
        val forged = AppleJwsFixture().signedTransaction(payload())

        assertFailsWith<InvalidAppleNotificationException> { decoder.decode(forged) }
    }

    @Test
    fun `다른 앱의 알림은 거부한다`() {
        assertFailsWith<InvalidAppleNotificationException> {
            decoder.decode(fixture.signedTransaction(payload(bundleId = "com.other.app")))
        }
        assertFailsWith<InvalidAppleNotificationException> {
            decoder.decode(fixture.signedTransaction(payload(appAppleId = 1L)))
        }
    }

    @Test
    fun `Xcode 나 로컬 테스트 환경의 알림은 거부한다`() {
        assertFailsWith<InvalidAppleNotificationException> {
            decoder.decode(fixture.signedTransaction(payload(environment = "Xcode", appAppleId = null)))
        }
    }

    @Test
    fun `JWS 가 아닌 문자열은 거부한다`() {
        assertFailsWith<InvalidAppleNotificationException> { decoder.decode("") }
        assertFailsWith<InvalidAppleNotificationException> { decoder.decode("not-a-jws") }
    }

    @Test
    fun `거래 정보가 없는 알림도 푼다`() {
        val notification = decoder.decode(fixture.signedTransaction(payload(signedTransactionInfo = null)))

        assertNull(notification.signedTransactionInfo)
    }

    private fun payload(
        environment: String = "Production",
        bundleId: String = BUNDLE_ID,
        appAppleId: Long? = APP_APPLE_ID,
        signedTransactionInfo: String? = "signed-transaction",
    ): Map<String, Any?> =
        mapOf(
            "notificationType" to "REFUND",
            "notificationUUID" to "uuid-1",
            "version" to "2.0",
            "signedDate" to SIGNED_AT.toEpochMilli(),
            "data" to
                mapOf(
                    "bundleId" to bundleId,
                    "appAppleId" to appAppleId,
                    "environment" to environment,
                    "signedTransactionInfo" to signedTransactionInfo,
                ).filterValues { it != null },
        )
}
