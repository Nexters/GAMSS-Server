package com.nexters.gamss.conversation.domain

import com.nexters.gamss.emotion.domain.EmotionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessageTest {
    @Test
    fun `사용자 메시지는 emotionType 없이 생성된다`() {
        val message = Message(conversationId = 1L, senderType = SenderType.USER, content = "오늘 힘들었어")

        assertEquals(null, message.emotionType)
    }

    @Test
    fun `캐릭터 메시지는 emotionType과 함께 생성된다`() {
        val message =
            Message(
                conversationId = 1L,
                senderType = SenderType.CHARACTER,
                emotionType = EmotionType.WARM,
                content = "많이 힘들었겠다",
            )

        assertEquals(EmotionType.WARM, message.emotionType)
    }

    @Test
    fun `사용자 메시지에 emotionType을 지정하면 예외`() {
        assertFailsWith<IllegalArgumentException> {
            Message(
                conversationId = 1L,
                senderType = SenderType.USER,
                emotionType = EmotionType.JOY,
                content = "내용",
            )
        }
    }

    @Test
    fun `캐릭터 메시지에 emotionType이 없으면 예외`() {
        assertFailsWith<IllegalArgumentException> {
            Message(conversationId = 1L, senderType = SenderType.CHARACTER, content = "내용")
        }
    }
}
