package com.nexters.gamss.conversation.search

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.global.crypto.BlindIndexer
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 블라인드 인덱스 위에서 FULLTEXT 구문 검색이 ngram 파서와 같게 동작하는지 고정한다.
 *
 * **이 검증이 설계 전체를 떠받친다.** 토큰을 집합으로만 맞추면 "가나다"를 찾을 때 "나다가나"처럼
 * 조각이 흩어져 있는 글까지 걸리는데, 토큰열을 원문 순서대로 이어 붙이고 구문 검색을 하면 인접·순서가
 * 강제돼 그 오탐이 사라진다. 여기서 어긋나면 별도 토큰 테이블 방식으로 되돌려야 한다.
 *
 * InnoDB 풀텍스트 인덱스는 커밋 시점에 갱신되므로 [com.nexters.gamss.support.RepositoryTest]의
 * @Transactional 롤백을 쓰지 않고 실제로 커밋한 뒤 검색한다(수동 정리).
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class BlindIndexPhraseSearchIntegrationTest {
    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @Autowired
    private lateinit var indexer: BlindIndexer

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @AfterEach
    fun cleanUp() {
        messageRepository.deleteAll()
        conversationRepository.deleteAll()
    }

    @Test
    fun `저장한 내용의 일부로 검색하면 걸린다`() {
        val id = saveMessage("오늘은 회사에서 정말 힘들었다")

        assertEquals(setOf(id), searchByBlindIndex("회사에서"))
    }

    @Test
    fun `두 글자 검색어도 걸린다`() {
        // 토큰이 하나뿐인 경우다. 최소 검색어 길이(2자) 규약이 그대로 성립해야 한다.
        val id = saveMessage("오늘은 회사에서 정말 힘들었다")

        assertEquals(setOf(id), searchByBlindIndex("회사"))
    }

    @Test
    fun `조각이 흩어져 있으면 걸리지 않는다`() {
        // "나다가나"는 "가나"와 "나다"를 둘 다 갖고 있지만 그 순서로 붙어 있지는 않다.
        // 집합 매칭이라면 걸리고, 구문 검색이라면 걸리지 않는다 — 이 차이를 고정하는 테스트다.
        saveMessage("나다가나")

        assertTrue(searchByBlindIndex("가나다").isEmpty(), "순서와 인접이 강제돼야 한다")
    }

    @Test
    fun `어절이 떨어진 검색어는 원문에서도 붙어 있어야 걸린다`() {
        val adjacent = saveMessage("회사 스트레스가 심하다")
        saveMessage("회사에서 받은 스트레스가 심하다")

        // ngram 파서가 공백을 넘는 토큰을 만들지 않는 것과 같은 규칙이다.
        assertEquals(setOf(adjacent), searchByBlindIndex("회사 스트레스"))
    }

    @Test
    fun `영문 대소문자를 구분하지 않는다`() {
        val id = saveMessage("Github 이슈를 정리했다")

        assertEquals(setOf(id), searchByBlindIndex("github"))
    }

    @Test
    fun `ngram 파서와 같은 규칙으로 걸린다`() {
        // 아래 기대값은 옛 ngram 인덱스가 아직 살아 있던 시점에 두 방식의 결과가 같음을 확인하고
        // 그대로 옮겨 적은 것이다(V33 에서 옛 인덱스를 지워 이제 나란히 비교할 수 없다).
        val hard = saveMessage("오늘은 회사에서 정말 힘들었다")
        val rain = saveMessage("퇴근길에 비를 맞아서 기분이 가라앉았다")
        val lunch = saveMessage("회사 동료와 점심을 먹으며 이야기했다")
        saveMessage("나다가나")

        assertEquals(setOf(hard, lunch), searchByBlindIndex("회사"))
        assertEquals(setOf(hard), searchByBlindIndex("회사에서"))
        assertEquals(setOf(rain), searchByBlindIndex("기분"))
        assertEquals(setOf(rain), searchByBlindIndex("비를 맞아서"))
        assertEquals(setOf(lunch), searchByBlindIndex("점심"))
        assertTrue(searchByBlindIndex("가나다").isEmpty(), "조각이 흩어진 글은 안 걸린다")
        assertTrue(searchByBlindIndex("없는말").isEmpty())
    }

    private fun saveMessage(content: String): Long {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        val message = messageRepository.save(conversation.createMessage(SenderType.USER, null, content, null))
        return message.id
    }

    /** 새 경로. 검색어를 같은 규칙으로 토큰열로 바꿔 구문 검색한다. */
    private fun searchByBlindIndex(keyword: String): Set<Long> {
        val tokens = indexer.tokenize(keyword)
        if (tokens.isEmpty()) {
            return emptySet()
        }
        return jdbcTemplate
            .queryForList(
                "select id from messages where match(content_index) against(? in boolean mode)",
                Long::class.java,
                "\"${tokens.joinToString(" ")}\"",
            ).filterNotNull()
            .toSet()
    }
}
