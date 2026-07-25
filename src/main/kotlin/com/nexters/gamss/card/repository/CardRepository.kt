package com.nexters.gamss.card.repository

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface CardRepository : JpaRepository<Card, Long> {
    fun existsByConversationId(conversationId: Long): Boolean

    /**
     * [start, end) 사이(대화 생성시간 기준)에 속한 회원의 카드를 오래된 순으로 조회한다.
     * 삭제된 채팅방의 카드는 제외한다 — 카드는 대화 종료 여부와 무관하게 삭제될 수 있어(Conversation.delete),
     * 삭제 후에도 남은 카드가 캘린더·날짜별 조회에 계속 나타나는 걸 막는다.
     */
    @Query(
        "select c from Card c, Conversation cv " +
            "where cv.id = c.conversationId and c.memberId = :memberId " +
            "and c.conversationCreatedAt >= :start and c.conversationCreatedAt < :end " +
            "and cv.status <> :excludedStatus " +
            "order by c.conversationCreatedAt asc, c.id asc",
    )
    fun findAllByMemberIdAndConversationCreatedAtInRange(
        @Param("memberId") memberId: Long,
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("excludedStatus") excludedStatus: ConversationStatus = ConversationStatus.DELETED,
    ): List<Card>
}
