package com.nexters.gamss.payment.apple

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource

@Configuration
class AppleIapConfig {
    @Bean
    fun appleTransactionDecoder(properties: AppleIapProperties): AppleTransactionDecoder {
        check(properties.bundleId.isNotBlank()) { "App Store bundleId(apple.iap.bundle-id)가 비어 있습니다." }
        check(properties.appAppleId > 0) { "App Store 앱 ID(apple.iap.app-apple-id)가 올바르지 않습니다: ${properties.appAppleId}" }
        return SignedAppleTransactionDecoder(listOf(rootCertificate()), properties.bundleId, properties.appAppleId)
    }

    /** apple.com/certificateauthority 의 Apple Root CA - G3. SHA-256 지문 63:34:3A:BF...:3E:91:79. */
    private fun rootCertificate(): ByteArray = ClassPathResource(ROOT_CERTIFICATE).inputStream.use { it.readBytes() }

    private companion object {
        const val ROOT_CERTIFICATE = "apple/AppleRootCA-G3.cer"
    }
}
