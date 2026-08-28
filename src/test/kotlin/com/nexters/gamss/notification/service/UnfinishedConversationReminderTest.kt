package com.nexters.gamss.notification.service

import com.nexters.gamss.card.config.CardProperties
import com.nexters.gamss.card.service.AutoCardWindow
import com.nexters.gamss.conversation.service.ConversationService
import com.nexters.gamss.conversation.service.UnfinishedConversation
import com.nexters.gamss.notification.domain.NotificationType
import com.nexters.gamss.notification.push.PushMessage
import com.nexters.gamss.notification.push.PushSendResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnfinishedConversationReminderTest {
    private val conversationService = mockk<ConversationService>()
    private val notifier = mockk<MemberPushNotifier>()
    private val notificationLogRecorder = mockk<NotificationLogRecorder>(relaxed = true)
    private val window = AutoCardWindow(CardProperties(autoCardStartDate = LocalDate.of(2026, 8, 15)))
    private val reminder = UnfinishedConversationReminder(conversationService, window, notifier, notificationLogRecorder)

    private fun target(
        conversationId: Long,
        memberId: Long,
    ) = UnfinishedConversation(conversationId, memberId)

    @Test
    fun `대상 회원마다 따로 보낸다`() {
        every {
            conversationService.findUnfinishedConversations(any(), any())
        } returns listOf(target(10L, 1L), target(20L, 2L))
        every { notifier.send(any(), any()) } returns PushSendResult.none()

        reminder.runFor(CREATED_AFTER, CREATED_BEFORE)

        verify(exactly = 1) { notifier.send(listOf(1L), any()) }
        verify(exactly = 1) { notifier.send(listOf(2L), any()) }
    }

    @Test
    fun `대상이 없으면 발송을 부르지 않는다`() {
        every { conversationService.findUnfinishedConversations(any(), any()) } returns emptyList()

        reminder.runFor(CREATED_AFTER, CREATED_BEFORE)

        verify(exactly = 0) { notifier.send(any(), any()) }
    }

    /** 조회에 넘기는 기간이 곧 5시 배치가 다룰 범위다. 여기서 바꿔 넘기면 두 배치의 대상이 갈라진다. */
    @Test
    fun `받은 기간을 그대로 조회에 넘긴다`() {
        val after = slot<Instant>()
        val before = slot<Instant>()
        every {
            conversationService.findUnfinishedConversations(capture(after), capture(before))
        } returns emptyList()

        reminder.runFor(CREATED_AFTER, CREATED_BEFORE)

        assertEquals(CREATED_AFTER, after.captured)
        assertEquals(CREATED_BEFORE, before.captured)
    }

    /**
     * 4시 30분 알림은 **곧 종료된다는 예고**까지만 한다. 카드가 만들어졌다는 소식은 5시 발송이
     * 따로 맡으므로(#154), 여기서 카드를 약속하면 두 알림이 같은 말을 하게 된다.
     */
    @Test
    fun `문구는 종료 예고까지만 하고 카드를 약속하지 않는다`() {
        every { conversationService.findUnfinishedConversations(any(), any()) } returns listOf(target(10L, 1L))
        val message = slot<PushMessage>()
        every { notifier.send(any(), capture(message)) } returns PushSendResult.none()

        reminder.runFor(CREATED_AFTER, CREATED_BEFORE)

        assertTrue(message.captured.title.contains("종료"))
        assertFalse(message.captured.body.contains("카드"))
    }

    /**
     * 발송은 회원당 한 번인데 기록은 방마다 남아야 한다. 회원 단위로만 남기면 백오피스가
     * "이 방 때문에 알림이 갔는가"를 답할 수 없다.
     */
    @Test
    fun `한 회원의 방이 여러 개면 그 회원에게 한 번 보내고 기록은 방마다 남는다`() {
        every {
            conversationService.findUnfinishedConversations(any(), any())
        } returns listOf(target(10L, 1L), target(20L, 1L), target(30L, 2L))
        val sent = PushSendResult(successCount = 1, failureCount = 0, invalidTokens = emptyList())
        every { notifier.send(any(), any()) } returns sent

        reminder.runFor(CREATED_AFTER, CREATED_BEFORE)

        verify(exactly = 1) { notifier.send(listOf(1L), any()) }
        verify(exactly = 1) {
            notificationLogRecorder.record(1L, listOf(10L, 20L), NotificationType.UNFINISHED_REMINDER, sent)
        }
        verify(exactly = 1) {
            notificationLogRecorder.record(2L, listOf(30L), NotificationType.UNFINISHED_REMINDER, sent)
        }
    }

    /**
     * 한 번에 몰아 보내면 결과가 전원분 합계로만 돌아와, 누구는 받고 누구는 기기가 없어도 전부
     * '발송'으로 기록된다. 대화방별 발송 결과라는 계약이 그 순간 깨진다.
     */
    @Test
    fun `회원마다 자기 발송 결과로 기록된다`() {
        every {
            conversationService.findUnfinishedConversations(any(), any())
        } returns listOf(target(10L, 1L), target(20L, 2L))
        val delivered = PushSendResult(successCount = 1, failureCount = 0, invalidTokens = emptyList())
        every { notifier.send(listOf(1L), any()) } returns delivered
        every { notifier.send(listOf(2L), any()) } returns PushSendResult.none()

        reminder.runFor(CREATED_AFTER, CREATED_BEFORE)

        verify(exactly = 1) {
            notificationLogRecorder.record(1L, listOf(10L), NotificationType.UNFINISHED_REMINDER, delivered)
        }
        verify(exactly = 1) {
            notificationLogRecorder.record(2L, listOf(20L), NotificationType.UNFINISHED_REMINDER, PushSendResult.none())
        }
    }

    /**
     * 회원마다 따로 보내므로 중간에 터지면 부분 상태로 끝난다. 앞선 회원은 알림을 받고 기록도
     * 남지만 뒤는 통째로 빠진다. 전원에게 한 번에 보내던 때는 예외가 곧 '아무도 못 받았다'였으므로,
     * 그때의 전제로 읽지 않도록 여기서 고정한다.
     */
    @Test
    fun `중간에 터지면 앞선 회원까지만 처리되고 뒤는 빠진다`() {
        every {
            conversationService.findUnfinishedConversations(any(), any())
        } returns listOf(target(10L, 1L), target(20L, 2L), target(30L, 3L))
        every { notifier.send(listOf(1L), any()) } returns PushSendResult.none()
        every { notifier.send(listOf(2L), any()) } throws RuntimeException("DB 끊김")

        assertFailsWith<RuntimeException> { reminder.runFor(CREATED_AFTER, CREATED_BEFORE) }

        verify(exactly = 1) {
            notificationLogRecorder.record(1L, listOf(10L), NotificationType.UNFINISHED_REMINDER, any<PushSendResult>())
        }
        verify(exactly = 0) { notifier.send(listOf(3L), any()) }
        verify(exactly = 0) { notificationLogRecorder.record(3L, any(), any(), any<PushSendResult>()) }
    }

    companion object {
        private val CREATED_AFTER: Instant = Instant.parse("2026-08-14T20:00:00Z")
        private val CREATED_BEFORE: Instant = Instant.parse("2026-08-17T20:00:00Z")
    }
}
