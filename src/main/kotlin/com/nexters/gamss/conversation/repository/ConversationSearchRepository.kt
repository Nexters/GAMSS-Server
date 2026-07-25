package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.Conversation
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 대화방 풀텍스트 검색 전용 리포지토리. 매핑 안정성을 위해 매칭된 대화방 ID만 최신순으로 돌려주고,
 * 실제 엔티티(제목·상태·일시)는 호출 측이 JPA로 로드한다.
 *
 * 카드는 대화방당 최대 1건(conversation_id UNIQUE)이라 LEFT JOIN 이 행을 늘리지 않으므로 COUNT(*)로 충분하다.
 */
interface ConversationSearchRepository : JpaRepository<Conversation, Long> {
    @Query(
        value = """
            SELECT c.id
            FROM conversations c
            LEFT JOIN cards ca ON ca.conversation_id = c.id
            WHERE c.member_id = :memberId
              AND (
                (ca.summary IS NOT NULL AND MATCH(ca.summary) AGAINST(:searchTerm IN BOOLEAN MODE))
                OR EXISTS (
                    SELECT 1 FROM messages m
                    WHERE m.conversation_id = c.id
                      AND MATCH(m.content) AGAINST(:searchTerm IN BOOLEAN MODE)
                )
              )
            ORDER BY c.created_at DESC
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM conversations c
            LEFT JOIN cards ca ON ca.conversation_id = c.id
            WHERE c.member_id = :memberId
              AND (
                (ca.summary IS NOT NULL AND MATCH(ca.summary) AGAINST(:searchTerm IN BOOLEAN MODE))
                OR EXISTS (
                    SELECT 1 FROM messages m
                    WHERE m.conversation_id = c.id
                      AND MATCH(m.content) AGAINST(:searchTerm IN BOOLEAN MODE)
                )
              )
        """,
        nativeQuery = true,
    )
    fun searchConversationIds(
        @Param("memberId") memberId: Long,
        @Param("searchTerm") searchTerm: String,
        pageable: Pageable,
    ): Page<Long>
}
