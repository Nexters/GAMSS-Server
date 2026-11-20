package com.nexters.gamss.payment.controller

import com.nexters.gamss.payment.controller.dto.AppleNotificationRequest
import com.nexters.gamss.payment.service.AppleNotificationOutcome
import com.nexters.gamss.payment.service.AppleNotificationService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * App Store Server Notifications V2 수신. 공개 경로이고 `signedPayload` 서명 검증이 유일한 인증이다.
 *
 * Apple 은 200~206 을 성공으로 보고, 그 밖의 응답이면 Production 에서만 1·12·24·48·72시간 뒤에 다시 보낸다(Sandbox 는
 * 다시 보내지 않는다). 처리했거나 처리할 필요가 없으면 200, 서명 실패는 400, 일시적인 실패는 503 이다. 응답 본문은 쓰지 않는다.
 */
@Hidden
@RestController
class AppleWebhookController(
    private val notificationService: AppleNotificationService,
) {
    @PostMapping("/api/webhooks/apple")
    fun receive(
        @RequestBody(required = false) body: AppleNotificationRequest?,
    ): ResponseEntity<Void> {
        val status =
            when (notificationService.handle(body?.signedPayload.orEmpty())) {
                AppleNotificationOutcome.ACK -> HttpStatus.OK
                AppleNotificationOutcome.REJECTED -> HttpStatus.BAD_REQUEST
                AppleNotificationOutcome.RETRY -> HttpStatus.SERVICE_UNAVAILABLE
            }
        return ResponseEntity.status(status).build()
    }
}
