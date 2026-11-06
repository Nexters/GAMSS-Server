package com.nexters.gamss.payment.domain

/**
 * 결제가 일어난 스토어. 이름이 `payment.platform` 과 원장 멱등 키(`purchase:{platform}:...`)에 그대로
 * 들어가므로, 상수 이름을 바꾸면 과거 결제와 원장 기록을 찾지 못한다.
 */
enum class PaymentPlatform {
    APPLE,
    GOOGLE,
}
