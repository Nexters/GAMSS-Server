package com.nexters.gamss.conversation.search

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationTitle
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
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
 * 블라인드 인덱스 검색 통합 테스트. 본문·제목이 암호문이 된 뒤에도 검색 계약이 그대로인지 고정한다
 * — 여기 있는 기대값은 ngram 풀텍스트를 쓰던 시절과 같다.
 *
 * InnoDB 풀텍스트 인덱스는 커밋 시점에 갱신되므로, [com.nexters.gamss.support.RepositoryTest]의
 * @Transactional 롤백을 쓰지 않고 실제로 커밋한 뒤 검색한다(수동 정리).
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class BlindIndexConversationSearcherIntegrationTest {
    @Autowired
    private lateinit var searcher: ConversationSearcher

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @AfterEach
    fun cleanUp() {
        messageRepository.deleteAll()
        conversationRepository.deleteAll()
    }

    @Test
    fun `채팅 내용으로 대화방을 검색한다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        messageRepository.save(
            Message(conversationId = conversation.id, senderType = SenderType.USER, content = "오늘 회사에서 너무 짜증났다"),
        )

        val result = searcher.search(memberId = 1L, keyword = "짜증", pageable = PageRequest.of(0, 20))

        assertEquals(1, result.totalElements.toInt())
        assertEquals(conversation.id, result.content.first().conversationId)
    }

    @Test
    fun `클라이언트가 지정한 제목으로 대화방을 검색하고 그 제목을 반환한다`() {
        val conversation =
            conversationRepository.save(
                Conversation(memberId = 1L).apply { rename(ConversationTitle("행복한 하루")) },
            )

        val result = searcher.search(memberId = 1L, keyword = "행복", pageable = PageRequest.of(0, 20))

        assertEquals(1, result.totalElements.toInt())
        val row = result.content.first()
        assertEquals(conversation.id, row.conversationId)
        assertEquals("행복한 하루", row.title)
    }

    @Test
    fun `저장한 뒤 제목을 바꾸면 새 제목으로 검색되고 옛 제목으로는 검색되지 않는다`() {
        // 제목은 [Conversation.rename] 으로 여러 번 바뀌므로 인덱스도 함께 갱신돼야 한다
        // ([com.nexters.gamss.conversation.search.ConversationSearchIndexListener] 의 @PreUpdate).
        // 갱신이 빠지면 제목만 바뀌고 인덱스는 옛 제목으로 남아, 저장은 됐는데 검색만 조용히 어긋난다.
        val conversation =
            conversationRepository.save(
                Conversation(memberId = 1L).apply { rename(ConversationTitle("행복한 하루")) },
            )

        val saved = conversationRepository.findById(conversation.id).orElseThrow()
        saved.rename(ConversationTitle("우울한 저녁"))
        conversationRepository.saveAndFlush(saved)

        val byNewTitle = searcher.search(memberId = 1L, keyword = "우울", pageable = PageRequest.of(0, 20))
        assertEquals(1, byNewTitle.totalElements.toInt())
        assertEquals("우울한 저녁", byNewTitle.content.first().title)

        val byOldTitle = searcher.search(memberId = 1L, keyword = "행복", pageable = PageRequest.of(0, 20))
        assertEquals(0, byOldTitle.totalElements.toInt())
    }

    @Test
    fun `삭제된 대화방은 제목이 맞아도 검색되지 않는다`() {
        conversationRepository.save(
            Conversation(memberId = 1L).apply {
                rename(ConversationTitle("행복한 하루"))
                delete()
            },
        )

        val result = searcher.search(memberId = 1L, keyword = "행복", pageable = PageRequest.of(0, 20))

        assertEquals(0, result.totalElements.toInt())
    }

    @Test
    fun `삭제된 대화방은 채팅 내용이 맞아도 검색되지 않는다`() {
        val deleted = conversationRepository.save(Conversation(memberId = 1L).apply { delete() })
        messageRepository.save(
            Message(conversationId = deleted.id, senderType = SenderType.USER, content = "삭제된 방의 짜증"),
        )
        val alive = conversationRepository.save(Conversation(memberId = 1L))
        messageRepository.save(
            Message(conversationId = alive.id, senderType = SenderType.USER, content = "살아있는 방의 짜증"),
        )

        val result = searcher.search(memberId = 1L, keyword = "짜증", pageable = PageRequest.of(0, 20))

        assertEquals(1, result.totalElements.toInt())
        assertEquals(alive.id, result.content.first().conversationId)
    }

    @Test
    fun `제목을 지정하지 않은 대화방은 제목이 null 로 나온다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        messageRepository.save(
            Message(conversationId = conversation.id, senderType = SenderType.USER, content = "제목 없는 짜증"),
        )

        val result = searcher.search(memberId = 1L, keyword = "짜증", pageable = PageRequest.of(0, 20))

        assertEquals(null, result.content.first().title)
    }

    @Test
    fun `다른 회원의 대화방은 검색되지 않는다`() {
        val mine = conversationRepository.save(Conversation(memberId = 1L))
        messageRepository.save(Message(conversationId = mine.id, senderType = SenderType.USER, content = "나도 짜증났어"))
        val others = conversationRepository.save(Conversation(memberId = 2L))
        messageRepository.save(Message(conversationId = others.id, senderType = SenderType.USER, content = "남의 짜증"))

        val result = searcher.search(memberId = 1L, keyword = "짜증", pageable = PageRequest.of(0, 20))

        assertEquals(1, result.totalElements.toInt())
        assertEquals(mine.id, result.content.first().conversationId)
        assertTrue(result.content.none { it.conversationId == others.id })
    }

    @Test
    fun `제목을 지정하지 않은 대화방은 클라이언트 표시 문구로 검색된다`() {
        val untitled = conversationRepository.save(Conversation(memberId = 1L))

        listOf("제목", "없는", "대화", "제목 없는", "없는 대화", "제목 없는 대화").forEach { keyword ->
            val result = searcher.search(memberId = 1L, keyword = keyword, pageable = PageRequest.of(0, 20))

            assertEquals(1, result.totalElements.toInt(), "\"$keyword\" 로 제목 없는 대화방이 검색돼야 한다")
            assertEquals(untitled.id, result.content.first().conversationId)
            assertEquals(null, result.content.first().title, "응답의 제목은 계속 null 이다")
        }
    }

    @Test
    fun `표시 문구와 어긋나는 검색어로는 제목을 지정하지 않은 대화방이 검색되지 않는다`() {
        conversationRepository.save(Conversation(memberId = 1L))

        // 저장된 제목을 찾을 때와 같은 규칙이라, 붙여 쓴 검색어("제목없는대화")와 원문에서 떨어진
        // 조합("제목 대화")은 걸리지 않는다. 실제 제목이 "제목 없는 대화" 인 방과 결과가 같아야 한다.
        listOf("제목없는대화", "제목 대화", "없는 제목", "짜증").forEach { keyword ->
            val result = searcher.search(memberId = 1L, keyword = keyword, pageable = PageRequest.of(0, 20))

            assertEquals(0, result.totalElements.toInt(), "\"$keyword\" 로는 검색되지 않아야 한다")
        }
    }

    @Test
    fun `모든 어절이 1글자인 검색어로는 제목 없는 대화방도 검색되지 않는다`() {
        // "제 목" 은 글자만 보면 표시 문구의 일부지만, 1글자 어절에서는 토큰이 나오지 않아 질의 자체를
        // 하지 않는다. 판정을 토큰이 아니라 문자열로 하면 이 검색어만 실제 제목과 다르게 동작한다.
        conversationRepository.save(Conversation(memberId = 1L))

        val result = searcher.search(memberId = 1L, keyword = "제 목", pageable = PageRequest.of(0, 20))

        assertEquals(0, result.totalElements.toInt())
    }

    @Test
    fun `제목 없는 대화방이 채팅 내용으로도 걸리면 한 번만 나온다`() {
        val untitled = conversationRepository.save(Conversation(memberId = 1L))
        messageRepository.save(
            Message(conversationId = untitled.id, senderType = SenderType.USER, content = "제목을 뭐라고 하지"),
        )

        val result = searcher.search(memberId = 1L, keyword = "제목", pageable = PageRequest.of(0, 20))

        assertEquals(1, result.totalElements.toInt(), "두 조건이 함께 맞아도 행이 늘어나면 안 된다")
        assertEquals(untitled.id, result.content.first().conversationId)
    }

    @Test
    fun `표시 문구로 검색하면 제목이 맞는 대화방과 제목 없는 대화방이 함께 나온다`() {
        conversationRepository.save(Conversation(memberId = 1L).apply { rename(ConversationTitle("제목 정하기")) })
        conversationRepository.save(Conversation(memberId = 1L))

        val result = searcher.search(memberId = 1L, keyword = "제목", pageable = PageRequest.of(0, 20))

        // 페이지 수가 어긋나지 않도록 countQuery 에도 같은 조건이 들어가야 한다.
        assertEquals(2, result.totalElements.toInt())
        assertEquals(2, result.content.size)
    }

    @Test
    fun `제목이 1글자 어절뿐이라 인덱스가 없는 대화방은 제목 없는 대화방으로 취급하지 않는다`() {
        // 1글자 어절에서는 토큰이 나오지 않아 title_index 가 null 이다. 화면에는 제목이 보이는 방이므로
        // 판정 기준을 title_index 로 잡으면 이 방이 섞여 들어온다.
        val oneLetterTitle = conversationRepository.save(Conversation(memberId = 1L).apply { rename(ConversationTitle("a")) })

        val result = searcher.search(memberId = 1L, keyword = "제목", pageable = PageRequest.of(0, 20))

        assertEquals(0, result.totalElements.toInt())
        assertTrue(result.content.none { it.conversationId == oneLetterTitle.id })
    }

    @Test
    fun `삭제된 방과 다른 회원의 제목 없는 대화방은 표시 문구로 검색되지 않는다`() {
        // 제목 없는 방을 찾는 조건이 매칭 괄호 밖에 있으면 AND 가 먼저 묶여 회원·삭제 조건을 건너뛴다.
        // 그 실수는 문법 오류가 아니라 조용히 통과하므로 여기서 막는다.
        val mine = conversationRepository.save(Conversation(memberId = 1L))
        val deleted = conversationRepository.save(Conversation(memberId = 1L).apply { delete() })
        val others = conversationRepository.save(Conversation(memberId = 2L))

        val result = searcher.search(memberId = 1L, keyword = "제목", pageable = PageRequest.of(0, 20))

        assertEquals(1, result.totalElements.toInt())
        assertEquals(mine.id, result.content.first().conversationId)
        assertTrue(result.content.none { it.conversationId in setOf(deleted.id, others.id) })
    }
}
