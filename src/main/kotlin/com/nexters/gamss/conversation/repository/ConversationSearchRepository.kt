package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 대화방 풀텍스트 검색 전용 리포지토리. 매핑 안정성을 위해 매칭된 대화방 ID만 최신순으로 돌려주고,
 * 실제 엔티티(제목·상태·일시)는 호출 측이 JPA로 로드한다.
 *
 * **검색 대상은 원문이 아니라 블라인드 인덱스 컬럼이다**(conversations.title_index,
 * messages.content_index). 원문은 암호문이라 매칭할 수 없다 — [searchTerm]도 원문이 아니라
 * [com.nexters.gamss.global.crypto.BlindIndexer]가 만든 토큰열 구문이어야 한다
 * ([com.nexters.gamss.conversation.search.BlindIndexConversationSearcher]가 만들어 넘긴다).
 *
 * 삭제된 대화방은 제외한다 — 목록·캘린더 등 다른 조회와 같은 규약이다.
 */
interface ConversationSearchRepository : JpaRepository<Conversation, Long> {
    @Query(
        value = """
            SELECT c.id
            FROM conversations c
            WHERE c.member_id = :memberId
              AND c.status <> :excludedStatus
              AND (
                (c.title_index IS NOT NULL AND MATCH(c.title_index) AGAINST(:searchTerm IN BOOLEAN MODE))
                OR EXISTS (
                    SELECT 1 FROM messages m
                    WHERE m.conversation_id = c.id
                      AND MATCH(m.content_index) AGAINST(:searchTerm IN BOOLEAN MODE)
                )
              )
            -- created_at 동률이면 페이지 간 순서가 흔들려 중복·누락이 생기므로 id 로 결정론을 준다.
            ORDER BY c.created_at DESC, c.id DESC
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM conversations c
            WHERE c.member_id = :memberId
              AND c.status <> :excludedStatus
              AND (
                (c.title_index IS NOT NULL AND MATCH(c.title_index) AGAINST(:searchTerm IN BOOLEAN MODE))
                OR EXISTS (
                    SELECT 1 FROM messages m
                    WHERE m.conversation_id = c.id
                      AND MATCH(m.content_index) AGAINST(:searchTerm IN BOOLEAN MODE)
                )
              )
        """,
        nativeQuery = true,
    )
    fun searchConversationIds(
        @Param("memberId") memberId: Long,
        @Param("searchTerm") searchTerm: String,
        pageable: Pageable,
        // native query라 엔티티의 @Enumerated(STRING) 매핑이 적용되지 않는다 — enum을 그대로 바인딩하면
        // 이 필터가 무력화되므로 String으로 받되, 값의 출처는 ConversationStatus.DELETED로 고정한다.
        @Param("excludedStatus") excludedStatus: String = ConversationStatus.DELETED.name,
    ): Page<Long>
}
