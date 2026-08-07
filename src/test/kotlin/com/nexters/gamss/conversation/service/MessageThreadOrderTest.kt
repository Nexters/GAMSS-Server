package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.emotion.domain.EmotionType
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageThreadOrderTest {
    private fun message(
        id: Long,
        senderType: SenderType,
        repliesToMessageId: Long? = null,
        emotionType: EmotionType? = null,
    ): Message =
        Message(
            conversationId = 1L,
            senderType = senderType,
            emotionType = emotionType,
            content = "id=$id",
            repliesToMessageId = repliesToMessageId,
        ).also { ReflectionTestUtils.setField(it, "id", id) }

    @Test
    fun `티키타카는 자신이 답장한 댓글 바로 다음으로 옮겨진다`() {
        val comment1 = message(1L, SenderType.CHARACTER, emotionType = EmotionType.JOY)
        val comment2 = message(2L, SenderType.CHARACTER, emotionType = EmotionType.ANGER)
        val tikitaka = message(3L, SenderType.CHARACTER, repliesToMessageId = 1L, emotionType = EmotionType.ANXIETY)

        val result = MessageThreadOrder.reorderTikitakaAfterTarget(listOf(comment1, comment2, tikitaka))

        assertEquals(listOf(1L, 3L, 2L), result.map { it.id })
    }

    @Test
    fun `유저 답글에 대한 캐릭터 응답은 재배치 대상이 아니라 원래 순서를 유지한다`() {
        val userMessage = message(1L, SenderType.USER)
        val userReply = message(2L, SenderType.USER, repliesToMessageId = 1L)
        val characterReply = message(3L, SenderType.CHARACTER, repliesToMessageId = 2L, emotionType = EmotionType.JOY)

        val result = MessageThreadOrder.reorderTikitakaAfterTarget(listOf(userMessage, userReply, characterReply))

        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
    }

    @Test
    fun `티키타카가 없으면 원래 순서를 그대로 유지한다`() {
        val messages = listOf(message(1L, SenderType.USER), message(2L, SenderType.CHARACTER, emotionType = EmotionType.JOY))

        val result = MessageThreadOrder.reorderTikitakaAfterTarget(messages)

        assertEquals(messages.map { it.id }, result.map { it.id })
    }
}
