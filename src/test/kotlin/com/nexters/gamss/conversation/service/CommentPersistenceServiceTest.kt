package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.CommentStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.parsing.CommentDraft
import com.nexters.gamss.llm.parsing.CommentFeed
import com.nexters.gamss.llm.parsing.TikitakaDraft
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@Import(TestcontainersConfig::class)
class CommentPersistenceServiceTest {
    @Autowired
    private lateinit var commentPersistenceService: CommentPersistenceService

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @AfterEach
    fun cleanUp() {
        messageRepository.deleteAll()
        conversationRepository.deleteAll()
    }

    private fun saveClaimedRootMessage(conversation: Conversation): Message {
        val rootMessage =
            messageRepository.save(
                conversation.createMessage(
                    senderType = SenderType.USER,
                    emotionType = null,
                    content = "오늘 있었던 일",
                    repliesToMessageId = null,
                ),
            )
        messageRepository.updateCommentStatus(rootMessage.id, CommentStatus.PENDING, listOf(CommentStatus.NONE), Instant.now())
        return rootMessage
    }

    @Test
    fun `티키타카는 자신이 답장한 댓글 바로 다음 순서로 반환된다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        val rootMessage = saveClaimedRootMessage(conversation)
        val feed =
            CommentFeed(
                comments =
                    listOf(
                        CommentDraft(EmotionType.JOY, "기쁨 댓글"),
                        CommentDraft(EmotionType.ANGER, "분노 댓글"),
                        CommentDraft(EmotionType.ANXIETY, "불안 댓글"),
                    ),
                tikitaka = listOf(TikitakaDraft(EmotionType.ANGER, EmotionType.JOY, "분노가 기쁨에게")),
            )

        val saved = commentPersistenceService.saveFeed(conversation.id, rootMessage.id, feed)

        assertEquals(
            listOf(EmotionType.JOY, EmotionType.ANGER, EmotionType.ANGER, EmotionType.ANXIETY),
            saved.map { it.emotionType },
            "기쁨 댓글 바로 다음에 그 댓글로 온 티키타카가 와야 한다",
        )
        val tikitakaMessage = saved[1]
        assertEquals(saved[0].id, tikitakaMessage.repliesToMessageId, "티키타카는 기쁨 댓글의 실제 messageId를 가리켜야 한다")
    }

    @Test
    fun `티키타카가 없으면 댓글만 순서대로 반환된다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        val rootMessage = saveClaimedRootMessage(conversation)
        val feed =
            CommentFeed(
                comments = listOf(CommentDraft(EmotionType.JOY, "기쁨 댓글")),
                tikitaka = emptyList(),
            )

        val saved = commentPersistenceService.saveFeed(conversation.id, rootMessage.id, feed)

        assertEquals(listOf(EmotionType.JOY), saved.map { it.emotionType })
    }
}
