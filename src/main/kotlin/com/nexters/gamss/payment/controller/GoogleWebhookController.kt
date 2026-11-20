package com.nexters.gamss.payment.controller

import com.nexters.gamss.payment.controller.dto.PubSubPushRequest
import com.nexters.gamss.payment.google.GooglePushAuthResult
import com.nexters.gamss.payment.google.GooglePushAuthenticator
import com.nexters.gamss.payment.service.GoogleNotificationOutcome
import com.nexters.gamss.payment.service.GoogleNotificationService
import io.swagger.v3.oas.annotations.Hidden
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/**
 * Google Play RTDN 수신(Pub/Sub 인증 push). 공개 경로이고, 우리 JWT 대신 Google OIDC 토큰으로 인증한다.
 *
 * Pub/Sub 은 102·200·201·202·204 만 ack 로 보고 나머지는 지수 백오프로 다시 보낸다. 처리했거나 처리할 필요가 없으면
 * 204, 인증 실패는 401·403, 일시적인 실패(Google 공개키 조회 실패, Google 과 통신할 수 없음)는 503 이다. 응답 본문은
 * 쓰지 않는다.
 */
@Hidden
@RestController
class GoogleWebhookController(
    private val authenticator: GooglePushAuthenticator,
    private val notificationService: GoogleNotificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/api/webhooks/google")
    fun receive(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody(required = false) body: PubSubPushRequest?,
    ): ResponseEntity<Void> {
        when (authenticator.authenticate(authorization)) {
            GooglePushAuthResult.MISSING -> return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            GooglePushAuthResult.REJECTED -> return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            GooglePushAuthResult.UNAVAILABLE -> return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
            GooglePushAuthResult.AUTHENTICATED -> Unit
        }
        val messageId = body?.message?.messageId
        if (messageId.isNullOrBlank()) {
            // 다시 보내도 같은 본문이라 ack 한다.
            log.error("Pub/Sub push 본문에 messageId 가 없습니다.")
            return ResponseEntity.noContent().build()
        }
        return when (notificationService.handle(messageId, body.message.data)) {
            GoogleNotificationOutcome.ACK -> ResponseEntity.noContent().build()
            GoogleNotificationOutcome.RETRY -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }
    }
}
