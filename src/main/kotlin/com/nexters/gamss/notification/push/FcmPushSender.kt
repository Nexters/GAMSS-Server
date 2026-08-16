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
        val sendable = sendable(tokens)
        if (sendable.isEmpty()) {
            return PushSendResult.none()
        }
        // FCM 은 한 번에 받는 토큰 수를 제한한다. 넘겨받은 목록의 크기를 호출하는 쪽이 신경 쓰지
        // 않도록 여기서 나눠 보낸다.
        return sendable
            .chunked(MAX_TOKENS_PER_REQUEST)
            .map { chunk -> sendChunk(chunk, message) }
            .reduce(::merge)
    }

    /**
     * 빈 토큰을 걸러낸다. [MulticastMessage] 는 하나라도 비어 있으면 **묶음 전체**를 거부하므로
     * (`none of the tokens can be null or empty`), 그대로 넘기면 빈 값 하나 때문에 같은 묶음의
     * 나머지 499건이 발송조차 되지 않는다.
     *
     * 저장 계층이 걸러줄 것이라고 가정하지 않는다 — 이 포트가 토큰을 [String] 으로 받기로 한 이상
     * 어떤 문자열이 들어올지는 여기서 책임진다.
     */
    private fun sendable(tokens: List<String>): List<String> {
        val sendable = tokens.filter { it.isNotBlank() }
        if (sendable.size != tokens.size) {
            // 빈 토큰이 저장돼 있다는 신호다. 발송은 계속하되 흔적을 남긴다.
            log.warn("빈 디바이스 토큰을 발송 대상에서 제외했다: {}건", tokens.size - sendable.size)
        }
        return sendable
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
        val result =
            PushSendResult(
                successCount = successCount,
                failureCount = responses.size - successCount,
                invalidTokens = invalidTokens,
            )
        // 실패 사유를 아는 것은 어댑터뿐이라 여기서 한 번 남긴다. 호출부에 맡기면 알림을 거는
        // 자리마다 같은 로그가 복사되고, 그전까지는 '푸시가 안 온다'를 쫓을 근거가 없다.
        if (result.failureCount > 0) {
            log.warn(
                "푸시 일부 실패: 성공={}건, 실패={}건, 무효토큰={}건",
                result.successCount,
                result.failureCount,
                result.invalidTokens.size,
            )
        }
        return result
    }

    private fun isInvalidToken(response: SendResponse): Boolean = response.exception?.messagingErrorCode in DEAD_TOKEN_CODES

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
        /** FCM 이 멀티캐스트 한 번에 받는 토큰 수 상한(`no more than 500 tokens can be specified`). */
        private const val MAX_TOKENS_PER_REQUEST = 500

        /**
         * 지워야 하는 실패 코드. **토큰이 죽었다는 것 말고 다른 설명이 없는 코드만** 넣는다.
         *
         * `UNREGISTERED` 는 앱이 지워졌거나 토큰이 갱신돼 이 토큰을 받는 기기가 없다는 뜻이고,
         * 그 원인은 이것 하나뿐이다.
         *
         * 재시도로 풀리지 않는데도 **일부러 뺀** 코드가 둘 있다. 둘 다 같은 이유다 — 토큰이
         * 죽었을 때와 **우리가 잘못했을 때** 구분 없이 같은 코드가 온다.
         *
         * - `INVALID_ARGUMENT`: 토큰 형식 오류뿐 아니라 **메시지 payload 오류**에도 온다. payload 는
         *   묶음 전체가 공유하므로, 문구를 잘못 만든 발송 한 번이면 모든 응답이 이 코드가 되고
         *   멀쩡한 기기의 등록이 전부 지워진다.
         * - `SENDER_ID_MISMATCH`: 다른 Firebase 프로젝트에 등록된 토큰이지만, 우리가 키를 잘못
         *   넣었을 때도 같은 코드가 온다.
         *
         * 대신 감수하는 것: 정말 망가진 토큰은 지워지지 않고 매 발송마다 같은 실패를 반복한다.
         * 그 신호는 부분 실패 로그로 남으므로, 특정 코드가 쌓이면 지울 것이 아니라 **우리 쪽을
         * 먼저 의심**하는 것이 맞다. 남는 쓰레기 토큰 몇 개보다 멀쩡한 등록을 지우는 쪽이 훨씬 비싸다.
         */
        private val DEAD_TOKEN_CODES = setOf(MessagingErrorCode.UNREGISTERED)
    }
}
