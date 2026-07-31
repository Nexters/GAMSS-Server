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

    /** 여러 대화방의 카드를 한 번에 조회한다(대화방 검색에서 제목=요약을 붙일 때 사용). */
    fun findByConversationIdIn(conversationIds: Collection<Long>): List<Card>

    /** 카드가 생성된 대화방 id들만 골라 반환한다(백오피스 대화방 사용량 페이지의 카드 생성 여부 배치 조회). */
    @Query("select c.conversationId from Card c where c.conversationId in :conversationIds")
    fun findConversationIdsIn(
        @Param("conversationIds") conversationIds: Collection<Long>,
    ): List<Long>

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

    /** [from, to) 사이(대화 생성시간 기준) 카드 수. 대시보드의 '오늘 카드 수' KPI. */
    @Query("select count(c) from Card c where c.conversationCreatedAt >= :from and c.conversationCreatedAt < :to")
    fun countCreatedBetween(
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): Long

    /** [from] 이후 카드의 감정별 개수. 대시보드 감정 분포. */
    @Query(
        "select c.emotion as emotion, count(c) as count from Card c " +
            "where c.conversationCreatedAt >= :from group by c.emotion",
    )
    fun countByEmotionSince(
        @Param("from") from: Instant,
    ): List<EmotionCountProjection>

    /** [from] 이후 카드의 생성시각(대화 생성 기준). 대시보드 일별 카드 추이 집계용(앱에서 KST 날짜로 묶는다). */
    @Query("select c.conversationCreatedAt from Card c where c.conversationCreatedAt >= :from")
    fun findCreatedAtsSince(
        @Param("from") from: Instant,
    ): List<Instant>
}
