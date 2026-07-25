package com.nexters.gamss.conversation.search

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MySQL 풀텍스트(ngram) 검색 통합 테스트.
 *
 * InnoDB 풀텍스트 인덱스는 커밋 시점에 갱신되므로, [com.nexters.gamss.support.RepositoryTest]의
 * @Transactional 롤백을 쓰지 않고 실제로 커밋한 뒤 검색한다(수동 정리).
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class MysqlConversationSearchIntegrationTest {
    @Autowired
    private lateinit var searchPort: ConversationSearchPort

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @Autowired
    private lateinit var cardRepository: CardRepository

    @AfterEach
    fun cleanUp() {
        cardRepository.deleteAll()
        messageRepository.deleteAll()
        conversationRepository.deleteAll()
    }

    @Test
    fun `채팅 내용으로 대화방을 검색한다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        messageRepository.save(
            Message(conversationId = conversation.id, senderType = SenderType.USER, content = "오늘 회사에서 너무 짜증났다"),
        )

        val result = searchPort.search(memberId = 1L, keyword = "짜증", pageable = PageRequest.of(0, 20))

        assertEquals(1, result.totalElements.toInt())
        assertEquals(conversation.id, result.content.first().conversationId)
    }

    @Test
    fun `제목(카드 요약)으로 대화방을 검색하고 제목을 채워 반환한다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        cardRepository.save(
            Card(
                memberId = 1L,
                conversationId = conversation.id,
                emotion = EmotionType.JOY,
                summary = "행복한 하루",
                message = "오늘 참 좋았다",
                conversationCreatedAt = conversation.createdAt,
            ),
        )

        val result = searchPort.search(memberId = 1L, keyword = "행복", pageable = PageRequest.of(0, 20))

        assertEquals(1, result.totalElements.toInt())
        val row = result.content.first()
        assertEquals(conversation.id, row.conversationId)
        assertEquals("행복한 하루", row.title)
    }

    @Test
    fun `다른 회원의 대화방은 검색되지 않는다`() {
        val mine = conversationRepository.save(Conversation(memberId = 1L))
        messageRepository.save(Message(conversationId = mine.id, senderType = SenderType.USER, content = "나도 짜증났어"))
        val others = conversationRepository.save(Conversation(memberId = 2L))
        messageRepository.save(Message(conversationId = others.id, senderType = SenderType.USER, content = "남의 짜증"))

        val result = searchPort.search(memberId = 1L, keyword = "짜증", pageable = PageRequest.of(0, 20))

        assertEquals(1, result.totalElements.toInt())
        assertEquals(mine.id, result.content.first().conversationId)
        assertTrue(result.content.none { it.conversationId == others.id })
    }
}
