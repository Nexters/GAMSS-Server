package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 대화 기록 구간을 읽는 두 쿼리가 실제 MySQL에서 범위, 순서, 개수를 지키는지 검증한다. */
@SpringBootTest
@Import(TestcontainersConfig::class)
class MessageRepositoryTranscriptQueryTest {
    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @AfterEach
    fun cleanUp() {
        messageRepository.deleteAll()
        conversationRepository.deleteAll()
    }

    private fun save(
        conversationId: Long,
        content: String,
        emotionType: EmotionType? = null,
    ): Message =
        messageRepository.save(
            Message(
                conversationId = conversationId,
                senderType = if (emotionType == null) SenderType.USER else SenderType.CHARACTER,
                emotionType = emotionType,
                content = content,
            ),
        )

    @Test
    fun `기준 메시지 직전까지 이 방의 메시지만 최신순으로 개수만큼 읽는다`() {
        val room = conversationRepository.save(Conversation(memberId = 1L)).id
        val other = conversationRepository.save(Conversation(memberId = 1L)).id
        save(room, "1")
        save(room, "2")
        save(other, "다른 방")
        save(room, "3")
        val current = save(room, "이번 메시지")
        save(room, "나중 메시지")

        val recent = messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(room, current.id, PageRequest.of(0, 2))

        assertEquals(listOf("3", "2"), recent.map { it.content })
    }

    @Test
    fun `기준 메시지 직전까지 그 캐릭터가 마지막으로 한 말을 읽는다`() {
        val room = conversationRepository.save(Conversation(memberId = 1L)).id
        save(room, "첫 번째 말", EmotionType.JOY)
        save(room, "두 번째 말", EmotionType.JOY)
        save(room, "분노의 말", EmotionType.ANGER)
        val current = save(room, "기쁨아 그게 뭐야")
        save(room, "나중 말", EmotionType.JOY)

        val latest = messageRepository.findFirstByConversationIdAndEmotionTypeAndIdLessThanOrderByIdDesc(room, EmotionType.JOY, current.id)

        assertEquals("두 번째 말", latest?.content)
        assertNull(
            messageRepository.findFirstByConversationIdAndEmotionTypeAndIdLessThanOrderByIdDesc(room, EmotionType.QUIRKY, current.id),
        )
    }
}
