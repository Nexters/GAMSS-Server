package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.prompt.ConversationTranscript
import com.nexters.gamss.llm.prompt.TranscriptEntry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageRequest
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

    private fun stubRecent(vararg newestFirst: Message) {
        every {
            messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(10L, 3L, PageRequest.of(0, ConversationTranscript.MAX_ENTRIES))
        } returns newestFirst.toList()
    }

    @Test
    fun `최신순으로 읽은 말을 시간순으로 되돌리고 유저와 캐릭터를 화자로 구분한다`() {
        stubRecent(message(2L, "비 오면 우산을 거꾸로 써!", EmotionType.JOY), message(1L, "오늘 좀 이상해"))

        val transcript = reader.read(conversationId = 10L, beforeMessageId = 3L)

        assertEquals(
            listOf(TranscriptEntry(null, "오늘 좀 이상해"), TranscriptEntry(EmotionType.JOY, "비 오면 우산을 거꾸로 써!")),
            transcript.entries,
        )
    }

    @Test
    fun `부른 캐릭터가 읽은 범위 안에 없으면 그 캐릭터의 마지막 발언만 따로 읽어 시간순 자리에 넣는다`() {
        stubRecent(message(2L, "오늘 좀 이상해"))
        every {
            messageRepository.findFirstByConversationIdAndEmotionTypeAndIdLessThanOrderByIdDesc(10L, EmotionType.JOY, 3L)
        } returns message(1L, "비 오면 우산을 거꾸로 써!", EmotionType.JOY)

        val transcript = reader.read(conversationId = 10L, beforeMessageId = 3L, pinned = listOf(EmotionType.JOY))

        assertEquals(
            listOf(TranscriptEntry(EmotionType.JOY, "비 오면 우산을 거꾸로 써!"), TranscriptEntry(null, "오늘 좀 이상해")),
            transcript.entries,
        )
    }

    @Test
    fun `부른 캐릭터가 읽은 범위 안에 있으면 따로 읽지 않는다`() {
        stubRecent(message(2L, "비 오면 우산을 거꾸로 써!", EmotionType.JOY), message(1L, "오늘 좀 이상해"))

        reader.read(conversationId = 10L, beforeMessageId = 3L, pinned = listOf(EmotionType.JOY))

        verify(exactly = 0) { messageRepository.findFirstByConversationIdAndEmotionTypeAndIdLessThanOrderByIdDesc(any(), any(), any()) }
    }
}
