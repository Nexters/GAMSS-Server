package com.nexters.gamss.notification.service

import com.nexters.gamss.notification.push.PushMessage
import com.nexters.gamss.notification.push.PushSendResult
import com.nexters.gamss.notification.push.PushSender
import com.nexters.gamss.notification.repository.DeviceTokenRepository
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 무효 토큰 정리가 실패해도 발송 결과를 잃지 않아야 한다.
 *
 * 정리는 발송이 끝난 뒤의 일이라, 여기서 예외가 밖으로 나가면 **이미 나간 알림의 결과까지** 호출자
 * (배치)가 실패로 받게 된다. DB 를 실제로 죽일 수 없어 리포지토리를 세워두고 던지게 한다.
 */
class MemberPushNotifierCleanupFailureTest {
    private val repository = mockk<DeviceTokenRepository>()
    private val notifier = MemberPushNotifier(repository, StubPushSender())

    @Test
    fun `정리가 실패해도 예외를 던지지 않고 발송 결과를 돌려준다`() {
        every { repository.findTokenValuesByMemberIdIn(any()) } returns listOf(DEAD_TOKEN)
        every { repository.deleteByTokenValueIn(any()) } throws IllegalStateException("DB 연결 끊김")

        val result = notifier.send(listOf(MEMBER_ID), MESSAGE)

        assertEquals(listOf(DEAD_TOKEN), result.invalidTokens)
        assertEquals(1, result.failureCount)
    }

    /** 받은 토큰을 전부 죽은 것으로 보고해 정리 경로를 타게 한다. */
    private class StubPushSender : PushSender {
        override fun send(
            tokens: List<String>,
            message: PushMessage,
        ): PushSendResult = PushSendResult(successCount = 0, failureCount = tokens.size, invalidTokens = tokens)
    }

    companion object {
        private const val MEMBER_ID = 1L
        private const val DEAD_TOKEN = "token-dead"
        private val MESSAGE = PushMessage(title = "제목", body = "본문")
    }
}
