package com.nexters.gamss.payment.apple

import com.apple.itunes.storekit.model.Environment
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload
import com.apple.itunes.storekit.verification.SignedDataVerifier
import com.apple.itunes.storekit.verification.VerificationException
import com.apple.itunes.storekit.verification.VerificationStatus
import com.nexters.gamss.payment.domain.PaymentEnvironment
import java.time.Instant

/**
 * [SignedDataVerifier.verifyAndDecodeNotification] 으로 알림을 검증한다. 이 메서드는 서명과 함께 bundleId·앱 ID·환경도 확인한다.
 *
 * [SignedAppleTransactionDecoder] 와 같은 이유로 Production 과 Sandbox 검증기를 하나씩 두고 Production 부터 시도하며,
 * 온라인 검사(OCSP)는 끈다.
 *
 * 거래 디코더와 달리 앱 식별자 불일치(`INVALID_APP_IDENTIFIER`)도 다음 검증기로 넘긴다. Sandbox 알림에는 `appAppleId` 가
 * 없는데, Production 검증기는 환경보다 앱 ID 를 먼저 비교해 환경 불일치가 아니라 앱 식별자 불일치로 거부한다. 서명은 검증기마다
 * 다시 확인하고, bundleId 가 다르면 Sandbox 검증기도 거부하므로 이렇게 넘겨도 걸러지는 알림은 같다.
 */
class SignedAppleNotificationDecoder(
    rootCertificates: List<ByteArray>,
    bundleId: String,
    appAppleId: Long,
) : AppleNotificationDecoder {
    private val verifiers =
        mapOf(
            PaymentEnvironment.PRODUCTION to Environment.PRODUCTION,
            PaymentEnvironment.SANDBOX to Environment.SANDBOX,
        ).mapValues { (_, environment) ->
            SignedDataVerifier(rootCertificates.map { it.inputStream() }.toSet(), bundleId, appAppleId, environment, false)
        }

    override fun decode(signedPayload: String): AppleNotification {
        val statuses = mutableListOf<VerificationStatus>()
        for ((environment, verifier) in verifiers) {
            try {
                return toNotification(verifier.verifyAndDecodeNotification(signedPayload), environment)
            } catch (e: VerificationException) {
                if (e.status !in RETRYABLE_STATUSES) {
                    throw InvalidAppleNotificationException("App Store 알림 검증 실패: status=${e.status}", e)
                }
                statuses += e.status
            } catch (e: RuntimeException) {
                // 인증 없이 열린 경로라 아무 문자열이나 온다. 라이브러리가 파싱 단계에서 던지는 예외도 검증 실패로 본다.
                throw InvalidAppleNotificationException("App Store 알림을 읽을 수 없습니다: ${e.javaClass.simpleName}", e)
            }
        }
        // 검증기마다 사유가 다르다(설정 오류면 앱 식별자, Xcode 알림이면 환경). 하나만 남기면 원인을 가린다.
        throw InvalidAppleNotificationException("Production 과 Sandbox 어느 검증기도 통과하지 못했습니다: statuses=$statuses")
    }

    private fun toNotification(
        payload: ResponseBodyV2DecodedPayload,
        environment: PaymentEnvironment,
    ): AppleNotification {
        val uuid = payload.notificationUUID
        val type = payload.rawNotificationType
        if (uuid.isNullOrBlank() || type.isNullOrBlank()) {
            throw InvalidAppleNotificationException("App Store 알림에 notificationUUID 나 notificationType 이 없습니다.")
        }
        return AppleNotification(
            notificationUuid = uuid,
            type = type,
            subtype = payload.rawSubtype,
            signedDate = payload.signedDate?.let { Instant.ofEpochMilli(it) },
            environment = environment,
            signedTransactionInfo = payload.data?.signedTransactionInfo,
        )
    }

    private companion object {
        val RETRYABLE_STATUSES = setOf(VerificationStatus.INVALID_ENVIRONMENT, VerificationStatus.INVALID_APP_IDENTIFIER)
    }
}
