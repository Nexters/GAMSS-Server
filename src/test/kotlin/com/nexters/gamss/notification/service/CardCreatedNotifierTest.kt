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

class CardCreatedNotifierTest {
    private val conversationRepository = mockk<ConversationRepository>()
    private val notifier = mockk<MemberPushNotifier>()
    private val window = AutoCardWindow(CardProperties(autoCardStartDate = LocalDate.of(2026, 8, 15)))
    private val cardCreatedNotifier = CardCreatedNotifier(conversationRepository, window, notifier)

    @Test
    fun `카드를 받은 회원들에게 한 번에 보낸다`() {
        every { conversationRepository.findMemberIdsWithCardCreatedSince(any(), any()) } returns listOf(1L, 2L)
        val memberIds = slot<Collection<Long>>()
        every { notifier.send(capture(memberIds), any()) } returns PushSendResult.none()

        cardCreatedNotifier.runSince(SINCE)

        assertEquals(listOf(1L, 2L), memberIds.captured.toList())
        verify(exactly = 1) { notifier.send(any(), any()) }
    }

    @Test
    fun `대상이 없으면 발송을 부르지 않는다`() {
        every { conversationRepository.findMemberIdsWithCardCreatedSince(any(), any()) } returns emptyList()

        cardCreatedNotifier.runSince(SINCE)

        verify(exactly = 0) { notifier.send(any(), any()) }
    }

    @Test
    fun `받은 기준 시각을 그대로 조회에 넘긴다`() {
        val since = slot<Instant>()
        every { conversationRepository.findMemberIdsWithCardCreatedSince(capture(since), any()) } returns emptyList()

        cardCreatedNotifier.runSince(SINCE)

        assertEquals(SINCE, since.captured)
    }

    /**
     * 카드 한 줄은 담지 않는다 — 잠금화면에 감정 기록이 그대로 뜨는 것은 이 서비스에서 특히 부담이 크다.
     * 4시 30분 알림이 종료 예고까지만 하는 것과 짝이 되는 규칙이다.
     */
    @Test
    fun `문구에 카드 내용을 담지 않는다`() {
        every { conversationRepository.findMemberIdsWithCardCreatedSince(any(), any()) } returns listOf(1L)
        val message = slot<PushMessage>()
        every { notifier.send(any(), capture(message)) } returns PushSendResult.none()

        cardCreatedNotifier.runSince(SINCE)

        assertTrue(message.captured.title.contains("카드"))
        assertFalse(message.captured.body.contains("\""), "카드 문구를 인용해 싣지 않는다")
    }

    companion object {
        private val SINCE: Instant = Instant.parse("2026-08-18T20:00:00Z")
    }
}
