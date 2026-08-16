package com.nexters.gamss.card.repository

import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 상한(50자) 도입 이전에 저장된 카드가 그대로 읽히는지 고정한다. Card의 init 블록이 JPA 로딩에서도
 * 돌면 기존 카드 조회가 전부 깨진다 — 새 불변식은 저장 경로에만 적용돼야 한다.
 */
class LegacyCardLoadTest : RepositoryTest() {
    @Autowired
    lateinit var cardRepository: CardRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `상한보다 긴 요약을 가진 기존 카드도 조회된다`() {
        val legacySummary = "가".repeat(500)
        jdbcTemplate.update(
            """
            insert into cards (member_id, conversation_id, emotion, summary, message, conversation_created_at)
            values (1, 1, 'ANGER', ?, ?, now(6))
            """.trimIndent(),
            legacySummary,
            legacySummary,
        )

        val loaded = cardRepository.findAll().single()

        assertEquals(legacySummary, loaded.summary)
    }
}
