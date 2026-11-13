package com.nexters.gamss.payment.google

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 기동 직후 RTDN push 구독 설정이 쓸 수 있는 상태인지 한 번 알린다.
 *
 * 설정이 없거나 반만 들어가도 앱은 뜨고, 모든 push 를 거부한다([OidcGooglePushAuthenticator]). 그대로 두면 첫 push 가
 * 거부될 때까지 아무도 모른다. 그래서 배포 직후 로그에 무엇이 빠졌는지 남긴다.
 *
 * **기동을 막지는 않는다.** RTDN 은 보조 경로다(지급의 기본은 앱 요청, 환불 회수의 기준은 매일 도는 대사 배치).
 * 배포 롤백은 이미지 태그만 되돌리고 `.env` 는 그대로 두므로, 기동을 막으면 되돌린 이미지도 같은 이유로 뜨지 못해
 * API 전체가 내려갈 수 있다. [com.nexters.gamss.llm.provider.GeminiConnectionStartupCheck] 와 같은 이유다.
 */
@Component
class GoogleRtdnStartupCheck(
    private val rtdn: GoogleRtdnProperties,
    private val play: GooglePlayProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun verify() {
        val missing =
            listOfNotNull(
                AUDIENCE_KEY.takeIf { rtdn.audience.isNullOrBlank() },
                SERVICE_ACCOUNT_KEY.takeIf { rtdn.serviceAccountEmail.isNullOrBlank() },
            )
        when {
            missing.isEmpty() -> {
                log.info("RTDN push 구독 설정 확인: audience 와 push 서비스 계정이 설정돼 있다.")
            }

            missing.size == 1 -> {
                log.error("RTDN push 구독 설정이 반만 들어 있어 모든 push 를 거부한다. 비어 있는 값: {}", missing.single())
            }

            !play.credentialsBase64.isNullOrBlank() -> {
                log.warn(
                    "Google Play 자격증명은 있는데 RTDN push 구독 설정({}, {})이 없어 모든 push 를 거부한다.",
                    AUDIENCE_KEY,
                    SERVICE_ACCOUNT_KEY,
                )
            }

            else -> {
                log.info("RTDN push 구독 설정이 없어 모든 push 를 거부한다(로컬·테스트 기본값).")
            }
        }
    }

    private companion object {
        const val AUDIENCE_KEY = "google.play.rtdn.audience"
        const val SERVICE_ACCOUNT_KEY = "google.play.rtdn.service-account-email"
    }
}
