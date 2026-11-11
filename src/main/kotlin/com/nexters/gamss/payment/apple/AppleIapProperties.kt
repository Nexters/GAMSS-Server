package com.nexters.gamss.payment.apple

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * App Store 거래 검증 설정. 둘 다 비밀이 아니라 `application.yml` 에 둔다.
 *
 * [appAppleId] 는 App Store Connect 의 앱 숫자 ID 다. Production 검증기를 만들 때 필수다.
 */
@ConfigurationProperties(prefix = "apple.iap")
class AppleIapProperties(
    val bundleId: String,
    val appAppleId: Long,
)
