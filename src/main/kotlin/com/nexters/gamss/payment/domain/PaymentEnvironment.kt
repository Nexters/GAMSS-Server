package com.nexters.gamss.payment.domain

/**
 * 결제가 일어난 스토어 환경. 앱 심사와 TestFlight 는 prod 서버로 Sandbox 결제를 보내므로 거부하지
 * 않고 지급하되, 매출 집계에서는 [SANDBOX] 를 뺀다(#237). Google 테스트 구매도 [SANDBOX] 다.
 */
enum class PaymentEnvironment {
    PRODUCTION,
    SANDBOX,
}
