package com.nexters.gamss.card.repository

import com.nexters.gamss.card.domain.Card
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface CardRepository : JpaRepository<Card, Long> {
    fun existsByConversationId(conversationId: Long): Boolean

    /** 여러 대화방의 카드를 한 번에 조회한다(대화방 검색에서 제목=요약을 붙일 때 사용). */
    fun findByConversationIdIn(conversationIds: Collection<Long>): List<Card>

    /** [start, end) 사이(대화 생성시간 기준)에 속한 회원의 카드를 오래된 순으로 조회한다. */
    @Query(
        "select c from Card c " +
            "where c.memberId = :memberId and c.conversationCreatedAt >= :start and c.conversationCreatedAt < :end " +
            "order by c.conversationCreatedAt asc, c.id asc",
    )
    fun findAllByMemberIdAndConversationCreatedAtInRange(
        @Param("memberId") memberId: Long,
        @Param("start") start: Instant,
        @Param("end") end: Instant,
    ): List<Card>
}
