package com.nexters.gamss.payment.google

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import kotlin.test.assertTrue

@ExtendWith(OutputCaptureExtension::class)
class GoogleRtdnStartupCheckTest {
    @Test
    fun `설정이 반만 들어 있으면 빠진 값을 ERROR 로 알린다`(output: CapturedOutput) {
        check(rtdn = GoogleRtdnProperties(AUDIENCE, null), play = GooglePlayProperties(null, null))

        assertTrue(output.out.lines().any { "ERROR" in it && "반만 들어 있어" in it && "google.play.rtdn.service-account-email" in it })
    }

    @Test
    fun `Google Play 자격증명은 있는데 RTDN 설정이 없으면 WARN 으로 알린다`(output: CapturedOutput) {
        check(rtdn = GoogleRtdnProperties(null, null), play = GooglePlayProperties("credentials", "com.nexters.gamss"))

        assertTrue(output.out.lines().any { "WARN" in it && "RTDN push 구독 설정" in it && "모든 push 를 거부한다" in it })
    }

    @Test
    fun `아무것도 없으면 INFO 한 줄만 남긴다`(output: CapturedOutput) {
        check(rtdn = GoogleRtdnProperties(null, null), play = GooglePlayProperties(null, null))

        assertTrue(output.out.lines().any { "INFO" in it && "로컬·테스트 기본값" in it })
    }

    @Test
    fun `둘 다 있으면 설정됐다고 남긴다`(output: CapturedOutput) {
        check(rtdn = GoogleRtdnProperties(AUDIENCE, "rtdn-push@gamss.iam.gserviceaccount.com"), play = GooglePlayProperties(null, null))

        assertTrue(output.out.lines().any { "INFO" in it && "audience 와 push 서비스 계정이 설정돼 있다" in it })
    }

    private fun check(
        rtdn: GoogleRtdnProperties,
        play: GooglePlayProperties,
    ) {
        GoogleRtdnStartupCheck(rtdn, play).verify()
    }

    private companion object {
        const val AUDIENCE = "https://dev-api.gamss.kr/api/webhooks/google"
    }
}
