package com.nexters.gamss.conversation.search

import com.nexters.gamss.conversation.domain.CommentStatus
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.conversation.service.CommentPersistenceService
import com.nexters.gamss.conversation.service.ConversationService
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.crypto.BlindIndexer
import com.nexters.gamss.llm.parsing.CommentDraft
import com.nexters.gamss.llm.parsing.CommentFeed
import com.nexters.gamss.llm.parsing.TikitakaDraft
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * **메시지를 저장하는 모든 경로에서 검색 인덱스가 채워지는지** 고정한다(#191).
 *
 * 인덱스 채우기를 서비스가 아니라 JPA 리스너에 둔 이유가 이것이다. 저장 경로가 세 곳이라
 * 서비스마다 채우면 한 곳만 빠뜨려도 **저장은 됐는데 검색만 안 되는** 메시지가 조용히 생긴다.
 * 리스너가 [com.nexters.gamss.conversation.domain.Message] 밖으로 나간 뒤에도 그 보장이 그대로인지
 * 여기서 확인한다.
 *
 * 기대값은 [BlindIndexer] 를 직접 돌린 결과와 맞춘다. 저장 경로와 검색 경로가 같은 토크나이저를
 * 쓰는지까지 함께 걸린다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class SearchIndexFillIntegrationTest {
    @Autowired
    private lateinit var conversationService: ConversationService

    @Autowired
    private lateinit var commentPersistenceService: CommentPersistenceService

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @Autowired
    private lateinit var indexer: BlindIndexer

    @AfterEach
    fun cleanUp() {
        messageRepository.deleteAll()
        conversationRepository.deleteAll()
    }

    @Test
    fun `사용자 메시지 저장 경로에서 인덱스가 채워진다`() {
        val message = conversationService.saveUserMessage(MEMBER_ID, null, "오늘 회사에서 너무 짜증났다")

        assertEquals(indexer.toIndexValue("오늘 회사에서 너무 짜증났다"), reloadContentIndex(message.id))
    }

    @Test
    fun `캐릭터 댓글·티키타카 저장 경로에서 인덱스가 채워진다`() {
        val root = conversationService.saveUserMessage(MEMBER_ID, null, "오늘 회사에서 너무 짜증났다")
        claimComment(root.id)

        val saved =
            commentPersistenceService.saveFeed(
                conversationId = root.conversationId,
                rootMessageId = root.id,
                feed =
                    CommentFeed(
                        comments = listOf(CommentDraft(EmotionType.ANGER, "그건 화날 만하지")),
                        tikitaka = listOf(TikitakaDraft(EmotionType.SADNESS, EmotionType.ANGER, "나는 좀 슬펐어")),
                    ),
            )

        assertEquals(2, saved.size)
        saved.forEach { message ->
            assertEquals(indexer.toIndexValue(message.content), reloadContentIndex(message.id), "content=${message.content}")
        }
    }

    @Test
    fun `유저 답글에 대한 캐릭터 재응답 저장 경로에서 인덱스가 채워진다`() {
        val root = conversationService.saveUserMessage(MEMBER_ID, null, "오늘 회사에서 너무 짜증났다")
        claimComment(root.id)
        val feed =
            commentPersistenceService.saveFeed(
                conversationId = root.conversationId,
                rootMessageId = root.id,
                feed = CommentFeed(comments = listOf(CommentDraft(EmotionType.ANGER, "그건 화날 만하지")), tikitaka = emptyList()),
            )
        val userReply =
            conversationService.saveUserMessage(MEMBER_ID, root.conversationId, "맞아 정말 억울했어", repliesToMessageId = feed.first().id)
        claimComment(userReply.id)

        val reply =
            commentPersistenceService.saveReply(
                conversationId = root.conversationId,
                rootMessageId = root.id,
                repliesToMessageId = userReply.id,
                characterId = EmotionType.ANGER,
                text = "억울한 건 말해야 풀려",
            )

        assertEquals(indexer.toIndexValue("맞아 정말 억울했어"), reloadContentIndex(userReply.id))
        assertEquals(indexer.toIndexValue("억울한 건 말해야 풀려"), reloadContentIndex(reply.id))
    }

    @Test
    fun `대화방 제목은 rename 으로 바뀔 때마다 인덱스가 다시 채워진다`() {
        val message = conversationService.saveUserMessage(MEMBER_ID, null, "오늘 회사에서 너무 짜증났다")

        conversationService.updateTitle(MEMBER_ID, message.conversationId, "행복한 하루")
        assertEquals(indexer.toIndexValue("행복한 하루"), reloadTitleIndex(message.conversationId))

        // @PreUpdate 가 빠지면 첫 제목의 인덱스만 남아 예전 제목으로 검색되는 방이 생긴다.
        conversationService.updateTitle(MEMBER_ID, message.conversationId, "우울한 하루")
        assertEquals(indexer.toIndexValue("우울한 하루"), reloadTitleIndex(message.conversationId))
    }

    /** 댓글 저장은 대상 메시지를 PENDING 으로 선점한 뒤에만 성립한다(실제 생성 흐름과 같은 전제). */
    private fun claimComment(messageId: Long) {
        messageRepository.updateCommentStatus(messageId, CommentStatus.PENDING, listOf(CommentStatus.NONE), Instant.now())
    }

    private fun reloadContentIndex(messageId: Long): String? {
        val message = messageRepository.findById(messageId).orElseThrow()
        assertNotNull(message.contentIndex, "저장은 됐는데 검색 인덱스가 비어 있다. messageId=$messageId")
        return message.contentIndex
    }

    private fun reloadTitleIndex(conversationId: Long): String? {
        val conversation = conversationRepository.findById(conversationId).orElseThrow()
        assertNotNull(conversation.titleIndex, "제목은 저장됐는데 검색 인덱스가 비어 있다. conversationId=$conversationId")
        return conversation.titleIndex
    }

    private companion object {
        const val MEMBER_ID = 1L
    }
}
