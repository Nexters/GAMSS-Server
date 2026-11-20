package com.nexters.gamss.payment.apple

import com.apple.itunes.storekit.client.AppStoreServerAPIClient
import com.apple.itunes.storekit.model.Environment
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.util.Base64

@Configuration
class AppleIapConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun appleTransactionDecoder(properties: AppleIapProperties): AppleTransactionDecoder {
        validate(properties)
        return SignedAppleTransactionDecoder(listOf(rootCertificate()), properties.bundleId, properties.appAppleId)
    }

    @Bean
    fun appleNotificationDecoder(properties: AppleIapProperties): AppleNotificationDecoder {
        validate(properties)
        return SignedAppleNotificationDecoder(listOf(rootCertificate()), properties.bundleId, properties.appAppleId)
    }

    /**
     * [com.nexters.gamss.payment.google.GooglePlayConfig] 와 같은 원칙이다. 키가 비어 있으면 "이 환경은 알림 기록 배치를 돌리지
     * 않는다"로 보고 뜬다. 일부만 있거나 읽을 수 없으면 기동을 막는다. 그대로 뜨면 배치가 매일 실패하는데, 그 상태는 놓친
     * 알림이 생겨야 드러난다.
     */
    @Bean
    fun appleServerApiClient(
        iap: AppleIapProperties,
        api: AppleServerApiProperties,
    ): AppleServerApiClient {
        if (!api.configured) {
            log.info("App Store Server API 키가 없어 알림 기록 배치를 돌리지 않는다(로컬·테스트 기본값).")
            return UnconfiguredAppleServerApiClient()
        }
        check(api.signingKeyBase64.isNotBlank() && api.keyId.isNotBlank() && api.issuerId.isNotBlank()) {
            "App Store Server API 설정(apple.iap.server-api)의 signing-key-base64, key-id, issuer-id 중 일부가 비어 있습니다."
        }
        val environment =
            when (api.environment) {
                "PRODUCTION" -> Environment.PRODUCTION
                "SANDBOX" -> Environment.SANDBOX
                else -> error("App Store Server API 환경(apple.iap.server-api.environment)은 PRODUCTION 이나 SANDBOX 여야 합니다: ${api.environment}")
            }
        val signingKey = String(Base64.getDecoder().decode(api.signingKeyBase64.filterNot { it.isWhitespace() }))
        return LibraryAppleServerApiClient(AppStoreServerAPIClient(signingKey, api.keyId, api.issuerId, iap.bundleId, environment))
    }

    private fun validate(properties: AppleIapProperties) {
        check(properties.bundleId.isNotBlank()) { "App Store bundleId(apple.iap.bundle-id)가 비어 있습니다." }
        check(properties.appAppleId > 0) { "App Store 앱 ID(apple.iap.app-apple-id)가 올바르지 않습니다: ${properties.appAppleId}" }
    }

    /** apple.com/certificateauthority 의 Apple Root CA - G3. SHA-256 지문 63:34:3A:BF...:3E:91:79. */
    private fun rootCertificate(): ByteArray = ClassPathResource(ROOT_CERTIFICATE).inputStream.use { it.readBytes() }

    private companion object {
        const val ROOT_CERTIFICATE = "apple/AppleRootCA-G3.cer"
    }
}
