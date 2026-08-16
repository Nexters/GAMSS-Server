package com.nexters.gamss.notification.push

import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.SendResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class FcmPushSenderTest {
    private val messaging = mockk<FirebaseMessaging>()
    private val sender = FcmPushSender(messaging)

    @Test
    fun `성공과 실패를 건수로 나눠 돌려준다`() {
        givenResponses(success(), failure(MessagingErrorCode.INTERNAL), success())

        val result = sender.send(listOf("a", "b", "c"), MESSAGE)

        assertEquals(2, result.successCount)
        assertEquals(1, result.failureCount)
    }

    @Test
    fun `죽은 토큰만 무효로 분류한다`() {
        givenResponses(
            failure(MessagingErrorCode.UNREGISTERED),
            failure(MessagingErrorCode.INVALID_ARGUMENT),
            failure(MessagingErrorCode.QUOTA_EXCEEDED),
            failure(MessagingErrorCode.UNAVAILABLE),
        )

        val result = sender.send(listOf("dead", "malformed", "quota", "unavailable"), MESSAGE)

        // 할당량 초과·일시 장애는 다음 발송에서 성공할 수 있는 값이라 지울 대상이 아니다.
        assertEquals(listOf("dead", "malformed"), result.invalidTokens)
        assertEquals(4, result.failureCount)
    }

    @Test
    fun `발송이 통째로 실패해도 예외를 던지지 않고 실패로 집계한다`() {
        every { messaging.sendEachForMulticast(any()) } throws IllegalStateException("FCM 응답 없음")

        val result = sender.send(listOf("a", "b"), MESSAGE)

        assertEquals(0, result.successCount)
        assertEquals(2, result.failureCount)
        // 근거 없이 지우면 FCM 장애 한 번에 멀쩡한 기기들의 등록이 사라진다.
        assertEquals(emptyList(), result.invalidTokens)
    }

    @Test
    fun `상한을 넘는 토큰은 나눠 보내고 결과를 합친다`() {
        var call = 0
        every { messaging.sendEachForMulticast(any()) } answers {
            batchResponseOf(List(CHUNK_SIZES[call++]) { success() })
        }

        val result = sender.send((1..TOKEN_COUNT).map { "token-$it" }, MESSAGE)

        verify(exactly = CHUNK_SIZES.size) { messaging.sendEachForMulticast(any()) }
        assertEquals(TOKEN_COUNT, result.successCount)
    }

    /**
     * FCM 은 토큰이 하나라도 비어 있으면 묶음 전체를 거부한다. 거르지 않으면 빈 값 하나 때문에
     * 같은 묶음의 나머지가 발송조차 되지 않는다.
     */
    @Test
    fun `빈 토큰은 제외하고 나머지는 정상 발송한다`() {
        givenResponses(success(), success())

        val result = sender.send(listOf("a", "", "   ", "b"), MESSAGE)

        assertEquals(2, result.successCount)
        assertEquals(0, result.failureCount)
        verify(exactly = 1) { messaging.sendEachForMulticast(any()) }
    }

    @Test
    fun `보낼 수 있는 토큰이 하나도 없으면 발송을 부르지 않는다`() {
        val result = sender.send(listOf("", "   "), MESSAGE)

        assertEquals(0, result.failureCount)
        verify(exactly = 0) { messaging.sendEachForMulticast(any()) }
    }

    @Test
    fun `대상이 없으면 발송을 부르지 않는다`() {
        val result = sender.send(emptyList(), MESSAGE)

        assertEquals(0, result.successCount)
        verify(exactly = 0) { messaging.sendEachForMulticast(any()) }
    }

    private fun givenResponses(vararg sendResponses: SendResponse) {
        every { messaging.sendEachForMulticast(any()) } returns batchResponseOf(sendResponses.toList())
    }

    private fun batchResponseOf(sendResponses: List<SendResponse>): BatchResponse =
        mockk<BatchResponse> {
            every { responses } returns sendResponses
        }

    private fun success(): SendResponse =
        mockk<SendResponse> {
            every { isSuccessful } returns true
        }

    private fun failure(errorCode: MessagingErrorCode): SendResponse =
        mockk<SendResponse> {
            every { isSuccessful } returns false
            every { exception } returns
                mockk<FirebaseMessagingException> {
                    every { messagingErrorCode } returns errorCode
                }
        }

    companion object {
        private val MESSAGE = PushMessage(title = "제목", body = "본문")

        private const val TOKEN_COUNT = 1001

        /** [TOKEN_COUNT] 개를 500 상한으로 나눈 결과. */
        private val CHUNK_SIZES = listOf(500, 500, 1)
    }
}
