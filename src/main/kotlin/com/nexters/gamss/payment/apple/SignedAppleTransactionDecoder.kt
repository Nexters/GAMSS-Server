package com.nexters.gamss.payment.apple

import com.apple.itunes.storekit.model.Environment
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload
import com.apple.itunes.storekit.verification.SignedDataVerifier
import com.apple.itunes.storekit.verification.VerificationException
import com.apple.itunes.storekit.verification.VerificationStatus
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.domain.PaymentEnvironment
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * `app-store-server-library` 의 [SignedDataVerifier] 로 거래 JWS 를 검증한다.
 *
 * 검증기 하나는 한 환경에만 묶이므로 Production 과 Sandbox 를 하나씩 두고 Production 부터 시도한다.
 * 앱 심사와 TestFlight 는 prod 서버로 Sandbox 거래를 보내므로 둘 다 받는다(#237). Xcode 와 로컬 테스트
 * 환경은 서명 검증을 건너뛰는 환경이라 검증기를 만들지 않아 거부된다.
 *
 * 온라인 검사(OCSP)는 끈다. 끄면 인증서 유효성을 JWS 의 서명 시각으로 판단해, 몇 달 뒤 다시 온 unfinished
 * 거래도 통과하고 요청 경로에 외부 호출이 없다. 켜면 Apple OCSP 장애가 iOS 결제 장애가 된다(#243).
 */
class SignedAppleTransactionDecoder(
    rootCertificates: List<ByteArray>,
    bundleId: String,
    appAppleId: Long,
) : AppleTransactionDecoder {
    private val log = LoggerFactory.getLogger(javaClass)

    private val verifiers =
        listOf(Environment.PRODUCTION, Environment.SANDBOX).map { environment ->
            SignedDataVerifier(rootCertificates.map { it.inputStream() }.toSet(), bundleId, appAppleId, environment, false)
        }

    override fun decode(signedTransaction: String): AppleTransaction = toTransaction(verify(signedTransaction))

    private fun verify(signedTransaction: String): JWSTransactionDecodedPayload {
        for (verifier in verifiers) {
            try {
                return verifier.verifyAndDecodeTransaction(signedTransaction)
            } catch (e: VerificationException) {
                if (e.status != VerificationStatus.INVALID_ENVIRONMENT) {
                    throw translate(e)
                }
            }
        }
        log.warn("Production 과 Sandbox 어느 쪽 거래도 아니라 거부했습니다(Xcode, 로컬 테스트 거래 등).")
        throw BusinessException(ErrorCode.PURCHASE_INVALID)
    }

    private fun translate(e: VerificationException): BusinessException {
        if (e.status == VerificationStatus.RETRYABLE_VERIFICATION_FAILURE) {
            log.warn("App Store 거래 검증이 일시적으로 실패했습니다: status={}", e.status)
            return BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
        }
        // 서명은 맞는데 bundleId 만 다르면 우리 설정이 틀렸을 수 있다(dev 빌드의 다른 bundleId 등).
        // PURCHASE_INVALID 면 앱이 finish 해 복구할 수 없으므로 finish 하지 않는 503 으로 둔다.
        if (e.status == VerificationStatus.INVALID_APP_IDENTIFIER) {
            log.error("App Store 거래의 bundleId 가 설정과 달라 거부했습니다. apple.iap.bundle-id 를 확인하세요.")
            return BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
        }
        log.warn("App Store 거래 서명을 확인할 수 없어 거부했습니다: status={}", e.status, e)
        return BusinessException(ErrorCode.PURCHASE_INVALID)
    }

    private fun toTransaction(payload: JWSTransactionDecodedPayload): AppleTransaction =
        AppleTransaction(
            transactionId = payload.transactionId,
            productId = payload.productId,
            type = payload.rawType,
            quantity = payload.quantity,
            appAccountToken = payload.appAccountToken?.toString(),
            purchaseDate = payload.purchaseDate?.let { Instant.ofEpochMilli(it) },
            revocationDate = payload.revocationDate?.let { Instant.ofEpochMilli(it) },
            environment = environmentOf(payload.environment),
        )

    private fun environmentOf(environment: Environment): PaymentEnvironment {
        if (environment == Environment.PRODUCTION) {
            return PaymentEnvironment.PRODUCTION
        }
        return PaymentEnvironment.SANDBOX
    }
}
