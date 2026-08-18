package com.nexters.gamss.notification.service

import com.nexters.gamss.notification.push.PushMessage
import com.nexters.gamss.notification.push.PushSendResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardCreatedNotifierTest {
    private val notifier = mockk<MemberPushNotifier>()
    private val cardCreatedNotifier = CardCreatedNotifier(notifier)

    @Test
    fun `그 회원에게만 보낸다`() {
        val memberIds = slot<Collection<Long>>()
        every { notifier.send(capture(memberIds), any()) } returns PushSendResult.none()

        cardCreatedNotifier.notifyCardCreated(MEMBER_ID)

        assertEquals(listOf(MEMBER_ID), memberIds.captured.toList())
    }

    /**
     * 부르는 쪽은 카드 생성 배치다. 알림이 터졌다고 이미 만들어진 카드가 실패로 집계되면 안 된다 —
     * 배치는 방 하나의 예외를 그 방의 실패로 처리한다.
     */
    /** 가드가 아닌 IllegalStateException(예: CAS 충돌)까지 배치를 멈추면 안 된다. */
    @Test
    fun `가드가 아닌 IllegalStateException 은 삼킨다`() {
        every { notifier.send(any(), any()) } throws IllegalStateException("CAS 충돌 같은 다른 상태 오류")

        cardCreatedNotifier.notifyCardCreated(MEMBER_ID)

        verify(exactly = 1) { notifier.send(any(), any()) }
    }

    @Test
    fun `발송이 실패해도 예외를 밖으로 내보내지 않는다`() {
        every { notifier.send(any(), any()) } throws RuntimeException("DB 연결 끊김")

        cardCreatedNotifier.notifyCardCreated(MEMBER_ID)

        verify(exactly = 1) { notifier.send(any(), any()) }
    }

    /**
     * 트랜잭션 가드([MemberPushNotifier])는 "배치를 트랜잭션으로 감싸지 마라"는 신호라 묻히면 안 된다.
     * 여기서 삼키면 그 실수가 로그 한 줄로 남고 배치는 초록불로 끝난다.
     */
    @Test
    fun `트랜잭션 가드 예외는 삼키지 않는다`() {
        every { notifier.send(any(), any()) } throws PushInTransactionException("트랜잭션 안에서 부를 수 없다")

        assertFailsWith<PushInTransactionException> { cardCreatedNotifier.notifyCardCreated(MEMBER_ID) }
    }

    /**
     * 카드 내용은 담지 않는다(잠금화면 노출). 장수도 담지 않는다 — 첫 카드 시점에는 그 사람이 최종
     * 몇 장을 받을지 알 수 없어서 "한 장"이라고 쓰면 틀린 말이 된다.
     */
    @Test
    fun `문구에 카드 내용과 장수를 담지 않는다`() {
        val message = slot<PushMessage>()
        every { notifier.send(any(), capture(message)) } returns PushSendResult.none()

        cardCreatedNotifier.notifyCardCreated(MEMBER_ID)

        assertTrue(message.captured.title.contains("카드"))
        assertFalse(message.captured.body.contains("한 장"))
    }

    companion object {
        private const val MEMBER_ID = 7L
    }
}
