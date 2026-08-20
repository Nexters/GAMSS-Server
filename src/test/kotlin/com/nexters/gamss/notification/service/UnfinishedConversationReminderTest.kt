package com.nexters.gamss.notification.service

import com.nexters.gamss.card.config.CardProperties
import com.nexters.gamss.card.service.AutoCardWindow
import com.nexters.gamss.conversation.repository.ConversationRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnfinishedConversationReminderTest {
    private val conversationRepository = mockk<ConversationRepository>()
    private val notifier = mockk<MemberPushNotifier>()
    private val window = AutoCardWindow(CardProperties(autoCardStartDate = LocalDate.of(2026, 8, 15)))
    private val reminder = UnfinishedConversationReminder(conversationRepository, window, notifier)

    @Test
    fun `대상 회원들에게 한 번에 보낸다`() {
        every { conversationRepository.findMemberIdsWithUnfinishedConversations(any(), any(), any()) } returns listOf(1L, 2L)
        val memberIds = slot<Collection<Long>>()
        every { notifier.send(capture(memberIds), any()) } returns PushSendResult.none()

        reminder.runFor(CREATED_AFTER, CREATED_BEFORE)

        assertEquals(listOf(1L, 2L), memberIds.captured.toList())
        verify(exactly = 1) { notifier.send(any(), any()) }
    }

    @Test
    fun `대상이 없으면 발송을 부르지 않는다`() {
        every { conversationRepository.findMemberIdsWithUnfinishedConversations(any(), any(), any()) } returns emptyList()

        reminder.runFor(CREATED_AFTER, CREATED_BEFORE)

        verify(exactly = 0) { notifier.send(any(), any()) }
    }

    /** 조회에 넘기는 기간이 곧 5시 배치가 다룰 범위다. 여기서 바꿔 넘기면 두 배치의 대상이 갈라진다. */
    @Test
    fun `받은 기간을 그대로 조회에 넘긴다`() {
        val after = slot<Instant>()
        val before = slot<Instant>()
        every {
            conversationRepository.findMemberIdsWithUnfinishedConversations(capture(after), capture(before), any())
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
        every { conversationRepository.findMemberIdsWithUnfinishedConversations(any(), any(), any()) } returns listOf(1L)
        val message = slot<PushMessage>()
        every { notifier.send(any(), capture(message)) } returns PushSendResult.none()

        reminder.runFor(CREATED_AFTER, CREATED_BEFORE)

        assertTrue(message.captured.title.contains("종료"))
        assertFalse(message.captured.body.contains("카드"))
    }

    companion object {
        private val CREATED_AFTER: Instant = Instant.parse("2026-08-14T20:00:00Z")
        private val CREATED_BEFORE: Instant = Instant.parse("2026-08-17T20:00:00Z")
    }
}
