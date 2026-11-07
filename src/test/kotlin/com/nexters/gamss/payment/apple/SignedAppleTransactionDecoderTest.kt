package com.nexters.gamss.payment.apple

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.apple.AppleJwsFixture.Companion.payload
import com.nexters.gamss.payment.domain.PaymentEnvironment
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 실제 서명과 인증서 체인으로 검증한다. 모킹하면 이 경계에서 지켜야 할 것을 하나도 확인하지 못한다. */
class SignedAppleTransactionDecoderTest {
    private val fixture = AppleJwsFixture()
    private val decoder = fixture.decoder()

    @Test
    fun `Production 거래를 풀어 필요한 값을 옮긴다`() {
        val revokedAt = Instant.parse("2026-11-07T00:00:00Z")
        val signed =
            fixture.signedTransaction(
                payload(quantity = 3, appAccountToken = "7F1C2A4E-3B5D-4C6E-8F90-A1B2C3D4E5F6", revocationDate = revokedAt),
            )

        val transaction = decoder.decode(signed)

        assertEquals(
            AppleTransaction(
                transactionId = "2000000123456789",
                productId = "token_300000",
                type = "Consumable",
                quantity = 3,
                appAccountToken = "7f1c2a4e-3b5d-4c6e-8f90-a1b2c3d4e5f6",
                purchaseDate = AppleJwsFixture.SIGNED_AT.minusSeconds(5),
                revocationDate = revokedAt,
                environment = PaymentEnvironment.PRODUCTION,
            ),
            transaction,
        )
    }

    @Test
    fun `Sandbox 거래도 받고 환경을 Sandbox 로 남긴다`() {
        val transaction = decoder.decode(fixture.signedTransaction(payload(environment = "Sandbox")))

        assertEquals(PaymentEnvironment.SANDBOX, transaction.environment)
    }

    @Test
    fun `서명 검증을 건너뛰는 Xcode 와 로컬 테스트 거래는 거부한다`() {
        listOf("Xcode", "LocalTesting").forEach { environment ->
            assertRejected(ErrorCode.PURCHASE_INVALID) { decoder.decode(fixture.signedTransaction(payload(environment = environment))) }
        }
    }

    @Test
    fun `다른 앱의 거래는 거부한다`() {
        assertRejected(ErrorCode.PURCHASE_INVALID) {
            decoder.decode(fixture.signedTransaction(payload(bundleId = "com.other.app")))
        }
    }

    @Test
    fun `신뢰 루트가 아닌 체인으로 서명한 거래는 거부한다`() {
        val forged = AppleJwsFixture().signedTransaction(payload())

        assertRejected(ErrorCode.PURCHASE_INVALID) { decoder.decode(forged) }
    }

    @Test
    fun `Apple 서명 인증서 확장이 없는 체인은 거부한다`() {
        val withoutExtension = AppleJwsFixture(signingOid = "1.2.3.4")

        assertRejected(ErrorCode.PURCHASE_INVALID) {
            withoutExtension.decoder().decode(withoutExtension.signedTransaction(payload()))
        }
    }

    @Test
    fun `서명 뒤에 바뀐 페이로드는 거부한다`() {
        val (header, _, signature) = fixture.signedTransaction(payload()).split(".")
        val tampered = fixture.signedTransaction(payload(quantity = 10)).split(".")[1]

        assertRejected(ErrorCode.PURCHASE_INVALID) { decoder.decode("$header.$tampered.$signature") }
    }

    @Test
    fun `JWS 형식이 아니면 거부한다`() {
        listOf("", "not-a-jws", "a.b.c").forEach { raw ->
            assertRejected(ErrorCode.PURCHASE_INVALID) { decoder.decode(raw) }
        }
    }

    private fun assertRejected(
        errorCode: ErrorCode,
        block: () -> Unit,
    ) {
        val exception = assertFailsWith<BusinessException> { block() }
        assertEquals(errorCode, exception.errorCode)
    }
}
