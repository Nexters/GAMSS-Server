package com.nexters.gamss.notification.service

import com.nexters.gamss.notification.domain.NotificationLog
import com.nexters.gamss.notification.domain.NotificationOutcome
import com.nexters.gamss.notification.domain.NotificationType
import com.nexters.gamss.notification.push.PushSendResult
import com.nexters.gamss.notification.repository.NotificationLogRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 발송 결과를 어떤 값으로 남기는지 고정한다. 여기가 어긋나면 백오피스에서 **대응할 것과 아닌 것이
 * 구분되지 않는다.** '알림을 끈 회원'이 '실패'로 보이거나, 그 반대가 된다.
 */
class NotificationLogRecorderTest {
    private val notificationLogRepository = mockk<NotificationLogRepository>()
    private val recorder = NotificationLogRecorder(notificationLogRepository)

    private fun outcomeOf(result: PushSendResult): NotificationOutcome {
        val saved = slot<List<NotificationLog>>()
        every { notificationLogRepository.saveAll(capture(saved)) } returns emptyList()
        recorder.record(MEMBER_ID, listOf(CONVERSATION_ID), NotificationType.CARD_CREATED, result)
        return saved.captured.single().outcome
    }

    @Test
    fun `성공이 하나라도 있으면 발송으로 남긴다`() {
        // 기기를 여럿 등록한 회원은 일부만 실패할 수 있다. 그 사람은 알림을 받았으므로 실패가 아니다.
        val partial = PushSendResult(successCount = 1, failureCount = 2, invalidTokens = emptyList())

        assertEquals(NotificationOutcome.SENT, outcomeOf(partial))
    }

    @Test
    fun `보낼 기기가 없었으면 기기 없음으로 남긴다`() {
        assertEquals(NotificationOutcome.NO_DEVICE, outcomeOf(PushSendResult.none()))
    }

    /**
     * 발송이 예외로 끊긴 경우다. 예전에는 이것도 [PushSendResult.none] 으로 돌아와 '기기 없음'으로
     * 기록됐고, 그러면 백오피스에서 장애가 정상으로 보인다.
     */
    @Test
    fun `발송 시도가 실패했으면 실패로 남긴다`() {
        assertEquals(NotificationOutcome.FAILED, outcomeOf(PushSendResult.failed()))
    }

    @Test
    fun `대상 대화방이 없으면 아무것도 남기지 않는다`() {
        recorder.record(MEMBER_ID, emptyList(), NotificationType.CARD_CREATED, PushSendResult.none())

        verify(exactly = 0) { notificationLogRepository.saveAll(any<List<NotificationLog>>()) }
    }

    /** 기록은 관측용이라, 여기서 예외가 올라가면 배치가 그 방을 실패로 집계하고 알림이 두 번 간다. */
    @Test
    fun `기록이 실패해도 예외를 밖으로 내보내지 않는다`() {
        every { notificationLogRepository.saveAll(any<List<NotificationLog>>()) } throws RuntimeException("DB 끊김")

        recorder.record(MEMBER_ID, listOf(CONVERSATION_ID), NotificationType.CARD_CREATED, PushSendResult.none())
    }

    private companion object {
        const val MEMBER_ID = 1L
        const val CONVERSATION_ID = 10L
    }
}
