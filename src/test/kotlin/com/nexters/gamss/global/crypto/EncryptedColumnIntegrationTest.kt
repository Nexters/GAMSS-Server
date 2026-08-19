package com.nexters.gamss.global.crypto

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.support.RepositoryTest
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 암호화 컬럼이 실제 MySQL에서 왕복하는지 고정한다. 단위 테스트로는 [EncryptedStringConverter]가
 * 스프링 빈으로 생성돼 키를 주입받는지(Hibernate ↔ SpringBeanContainer 연결)를 확인할 수 없어,
 * 여기서 실제 저장·조회로 검증한다.
 *
 * 검증의 핵심은 **원본 SQL로 읽은 값이 평문이 아니어야 한다**는 것이다. 엔티티 왕복만 보면 컨버터가
 * 아예 안 걸렸을 때도 통과해버린다.
 */
class EncryptedColumnIntegrationTest : RepositoryTest() {
    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var entityManager: EntityManager

    /**
     * 영속성 컨텍스트를 비운다. 안 비우면 [org.springframework.data.jpa.repository.JpaRepository.findById]
     * 가 1차 캐시의 엔티티를 그대로 돌려줘, 컨버터가 읽기에서 빠져 있어도 테스트가 통과한다.
     */
    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `카드 요약은 암호문으로 저장되고 엔티티로 읽으면 평문이다`() {
        val summary = "오늘은 회사에서 힘든 일이 있었다"
        val card = cardRepository.save(Card(1L, 1L, EmotionType.SADNESS, summary, summary, Instant.now()))
        flushAndClear()

        val stored = jdbcTemplate.queryForObject("select summary from cards where id = ?", String::class.java, card.id)
        assertTrue(stored!!.startsWith("enc:"), "DB에는 암호문이 있어야 한다")
        assertEquals(summary, cardRepository.findById(card.id).get().summary, "엔티티로는 평문이 나와야 한다")
    }

    @Test
    fun `카드의 message 컬럼도 암호문으로 저장된다`() {
        val summary = "친구와 오래 통화했다"
        val card = cardRepository.save(Card(1L, 2L, EmotionType.JOY, summary, summary, Instant.now()))
        flushAndClear()

        val stored = jdbcTemplate.queryForObject("select message from cards where id = ?", String::class.java, card.id)
        assertTrue(stored!!.startsWith("enc:"), "summary 와 같은 값이지만 따로 암호화된다")
    }

    @Test
    fun `같은 값을 두 컬럼에 넣어도 암호문은 서로 다르다`() {
        val summary = "같은 한 줄"
        val card = cardRepository.save(Card(1L, 3L, EmotionType.ANGER, summary, summary, Instant.now()))
        flushAndClear()

        val row = jdbcTemplate.queryForMap("select summary, message from cards where id = ?", card.id)

        // IV가 매번 달라서다. 같으면 덤프에서 "두 컬럼이 같은 값"이라는 정보가 새어 나간다.
        assertTrue(row["summary"] != row["message"], "IV가 값마다 달라야 한다")
    }

    @Test
    fun `대화 요약은 암호문으로 저장된다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        conversation.updateSummary("퇴근길에 비를 맞았고 기분이 가라앉았다")
        flushAndClear()

        val stored =
            jdbcTemplate.queryForObject("select summary from conversations where id = ?", String::class.java, conversation.id)

        assertTrue(stored!!.startsWith("enc:"), "DB에는 암호문이 있어야 한다")
    }

    @Test
    fun `벌크 updateSummary 로 저장한 값도 암호문이 된다`() {
        // 엔티티를 거치지 않는 경로라 컨버터가 빠지기 쉽다. JPQL 파라미터 바인딩에도 컨버터가 걸리는지 본다.
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        val summary = "카드 생성 시점에 확정된 요약"

        conversationRepository.updateSummary(conversation.id, summary)

        val stored =
            jdbcTemplate.queryForObject("select summary from conversations where id = ?", String::class.java, conversation.id)
        assertTrue(stored!!.startsWith("enc:"), "벌크 UPDATE 도 암호화돼야 한다")
        assertEquals(summary, conversationRepository.findById(conversation.id).get().summary)
    }

    @Test
    fun `과거 요약 조회는 복호화된 평문을 돌려준다`() {
        // 네이티브 쿼리 경로다. 스칼라로 뽑으면 암호문이 그대로 LLM 프롬프트에 실린다.
        val memberId = 42L
        val past = conversationRepository.save(Conversation(memberId).apply { end() })
        val summary = "지난주에 이직 고민을 했다"
        past.updateSummary(summary)
        val current = conversationRepository.save(Conversation(memberId))
        flushAndClear()

        val picked = conversationRepository.findRandomPastSummaries(memberId, current.id, poolSize = 5, pickCount = 5)

        assertEquals(listOf(summary), picked, "복호화된 평문이어야 한다")
    }

    @Test
    fun `프리픽스가 없는 기존 평문 행도 그대로 읽힌다`() {
        // 암호화 도입 이전에 저장된 행. 백필하지 않으므로 읽기에서 흡수한다.
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        flushAndClear()
        jdbcTemplate.update("update conversations set summary = ? where id = ?", "암호화 이전 요약", conversation.id)
        entityManager.clear()

        val loaded =
            jdbcTemplate.queryForObject("select summary from conversations where id = ?", String::class.java, conversation.id)
        assertEquals("암호화 이전 요약", loaded, "테스트가 넣은 값 자체는 평문이다")
        assertEquals("암호화 이전 요약", conversationRepository.findById(conversation.id).get().summary)
    }
}
