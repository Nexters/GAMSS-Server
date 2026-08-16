package com.nexters.gamss.notification.push

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import com.google.firebase.messaging.SendResponse
import org.slf4j.LoggerFactory

/**
 * FCM 으로 실제 발송하는 구현.
 *
 * 토큰별 결과를 하나씩 본다. 멀티캐스트는 **일부 토큰만 실패**할 수 있어서, 응답을 통째로
 * 성공/실패로 접으면 죽은 토큰을 골라낼 수 없다.
 *
 * 예외는 밖으로 내보내지 않는다([PushSender] 계약). FCM 이 통째로 응답하지 않는 경우도 그 묶음을
 * 실패로 세고 넘어간다 — 알림을 부른 작업까지 끌고 넘어질 이유가 없다.
 */
class FcmPushSender(
    private val messaging: FirebaseMessaging,
) : PushSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(
        tokens: List<String>,
        message: PushMessage,
    ): PushSendResult {
        if (tokens.isEmpty()) {
            return PushSendResult.none()
        }
        // FCM 은 한 번에 받는 토큰 수를 제한한다. 넘겨받은 목록의 크기를 호출하는 쪽이 신경 쓰지
        // 않도록 여기서 나눠 보낸다.
        return tokens
            .chunked(MAX_TOKENS_PER_REQUEST)
            .map { chunk -> sendChunk(chunk, message) }
            .reduce(::merge)
    }

    private fun sendChunk(
        tokens: List<String>,
        message: PushMessage,
    ): PushSendResult =
        try {
            classify(tokens, messaging.sendEachForMulticast(multicastOf(tokens, message)).responses)
        } catch (e: Exception) {
            // 묶음 전체가 나가지 못한 경우다. 토큰이 죽었다는 근거가 없으므로 무효로 분류하지
            // 않는다 — 여기서 지워버리면 FCM 장애 한 번에 멀쩡한 기기들의 등록이 사라진다.
            log.error("푸시 발송 실패: 대상={}건, 제목={}", tokens.size, message.title, e)
            PushSendResult(successCount = 0, failureCount = tokens.size, invalidTokens = emptyList())
        }

    private fun classify(
        tokens: List<String>,
        responses: List<SendResponse>,
    ): PushSendResult {
        val invalidTokens = mutableListOf<String>()
        var successCount = 0
        responses.forEachIndexed { index, response ->
            if (response.isSuccessful) {
                successCount++
                return@forEachIndexed
            }
            if (isInvalidToken(response)) {
                invalidTokens += tokens[index]
            }
        }
        return PushSendResult(
            successCount = successCount,
            failureCount = responses.size - successCount,
            invalidTokens = invalidTokens,
        )
    }

    /**
     * 다시 시도해도 소용없는 실패인지 가른다.
     *
     * - `UNREGISTERED`: 앱이 지워졌거나 토큰이 갱신돼 이 토큰을 받는 기기가 없다.
     * - `INVALID_ARGUMENT`: FCM 이 토큰 형식 자체를 거부했다.
     *
     * 나머지(할당량 초과·FCM 일시 장애 등)는 다음 발송에서 성공할 수 있는 값이라 남겨둔다.
     */
    private fun isInvalidToken(response: SendResponse): Boolean =
        response.exception?.messagingErrorCode in
            setOf(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT)

    private fun multicastOf(
        tokens: List<String>,
        message: PushMessage,
    ): MulticastMessage =
        MulticastMessage
            .builder()
            .addAllTokens(tokens)
            .setNotification(
                Notification
                    .builder()
                    .setTitle(message.title)
                    .setBody(message.body)
                    .build(),
            ).build()

    private fun merge(
        left: PushSendResult,
        right: PushSendResult,
    ): PushSendResult =
        PushSendResult(
            successCount = left.successCount + right.successCount,
            failureCount = left.failureCount + right.failureCount,
            invalidTokens = left.invalidTokens + right.invalidTokens,
        )

    companion object {
        /** FCM 이 멀티캐스트 한 번에 받는 토큰 수 상한. */
        private const val MAX_TOKENS_PER_REQUEST = 500
    }
}
