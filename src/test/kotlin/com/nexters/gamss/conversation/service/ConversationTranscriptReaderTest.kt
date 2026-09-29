package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.prompt.TranscriptEntry
import io.mockk.every
import io.mockk.mockk
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationTranscriptReaderTest {
    private val messageRepository = mockk<MessageRepository>()
    private val reader = ConversationTranscriptReader(messageRepository)

    private fun message(
        id: Long,
        content: String,
        emotionType: EmotionType? = null,
    ): Message =
        Message(
            conversationId = 10L,
            senderType = if (emotionType == null) SenderType.USER else SenderType.CHARACTER,
            emotionType = emotionType,
            content = content,
        ).also { ReflectionTestUtils.setField(it, "id", id) }

    @Test
    fun `이번 메시지 직전까지만 읽고 유저와 캐릭터를 화자로 구분한다`() {
        // 이번 메시지는 [오늘 일기]로 따로 실리므로 여기 또 담기면 같은 말이 두 번 들어간다.
        every { messageRepository.findAllByConversationIdOrderByIdAsc(10L) } returns
            listOf(
                message(1L, "오늘 좀 이상해"),
                message(2L, "비 오면 우산을 거꾸로 써!", EmotionType.JOY),
                message(3L, "기쁨아 그게 무슨 소리야"),
                message(4L, "나중에 저장된 말", EmotionType.ANGER),
            )

        val transcript = reader.read(conversationId = 10L, beforeMessageId = 3L)

        assertEquals(
            listOf(TranscriptEntry(null, "오늘 좀 이상해"), TranscriptEntry(EmotionType.JOY, "비 오면 우산을 거꾸로 써!")),
            transcript.entries,
        )
    }
}
