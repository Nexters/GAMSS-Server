package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.CommentStatus
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface MessageRepository : JpaRepository<Message, Long> {
    fun findAllByConversationIdOrderByIdAsc(conversationId: Long): List<Message>

    /** 특정 발신주체의 메시지만 작성순으로 조회한다(카드 감정 분류는 유저 메시지만 입력으로 쓴다). */
    fun findAllByConversationIdAndSenderTypeOrderByIdAsc(
        conversationId: Long,
        senderType: SenderType,
    ): List<Message>

    /** 특정 일기(root) 메시지에 달린 캐릭터 댓글·티키타카 전체를 작성순으로 조회한다. */
    fun findAllByRootMessageIdOrderByIdAsc(rootMessageId: Long): List<Message>

    /** [repliesToMessageId]에 캐릭터가 재응답한 메시지를 찾는다(답글 1개당 캐릭터 응답 최대 1개). */
    fun findByRepliesToMessageId(repliesToMessageId: Long): Message?

    /**
     * 여러 대화방의 발신주체별 메시지 수를 한 번에 집계한다(백오피스 대화방 사용량 페이지).
     * 페이지에 올라온 대화방 id들만 넘겨 N+1 없이 유저/캐릭터 메시지 개수를 채운다.
     */
    @Query(
        "select m.conversationId as conversationId, m.senderType as senderType, count(m) as count " +
            "from Message m where m.conversationId in :conversationIds group by m.conversationId, m.senderType",
    )
    fun countBySenderForConversations(
        @Param("conversationIds") conversationIds: Collection<Long>,
    ): List<ConversationSenderCountProjection>

    /**
     * [messageId]의 commentStatus가 [fromAny] 중 하나일 때만 [to]로 원자적으로 바꾼다.
     * 영향받은 행 수(0 또는 1)로 선점 성공 여부를 판단한다 — check-then-act 레이스 없이
     * DB 행 락 안에서 확인과 변경이 한 번에 처리된다. [now]는 이 상태 전이 시각(고아 PENDING 판정 기준)이다.
     *
     * @Transactional을 명시한다 — 리포지토리 기본 트랜잭션에 기대지 않고 커밋 시점을 이 메서드
     * 하나로 짧게 고정한다(실제 DB로 검증 중 명시 안 했을 때 TransactionRequiredException 확인함).
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
        "update Message m set m.commentStatus = :to, m.commentStatusUpdatedAt = :now " +
            "where m.id = :messageId and m.commentStatus in :fromAny",
    )
    fun updateCommentStatus(
        @Param("messageId") messageId: Long,
        @Param("to") to: CommentStatus,
        @Param("fromAny") fromAny: List<CommentStatus>,
        @Param("now") now: Instant,
    ): Int

    /** 배포 중단·서버 크래시 등으로 [olderThan]보다 오래 PENDING에 머문 고아 상태를 NONE으로 되돌린다. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
        "update Message m set m.commentStatus = :to, " +
            "m.commentStatusUpdatedAt = null " +
            "where m.commentStatus = :from " +
            "and m.commentStatusUpdatedAt < :olderThan",
    )
    fun resetStalePending(
        @Param("olderThan") olderThan: Instant,
        @Param("to") to: CommentStatus = CommentStatus.NONE,
        @Param("from") from: CommentStatus = CommentStatus.PENDING,
    ): Int

    /** [senderType] 메시지 중 [from, to) 사이 작성 수. 대시보드의 '오늘 메시지 수'(유저/캐릭터별) KPI. */
    @Query(
        "select count(m) from Message m " +
            "where m.senderType = :senderType and m.createdAt >= :from and m.createdAt < :to",
    )
    fun countBySenderTypeCreatedBetween(
        @Param("senderType") senderType: SenderType,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): Long

    /**
     * [from, to) 사이 [senderType] 메시지를 쓴 서로 다른 회원 수(활동 회원 = DAU/WAU).
     * 메시지는 회원을 직접 참조하지 않으므로 대화방을 통해 memberId 로 묶는다.
     */
    @Query(
        "select count(distinct c.memberId) from Message m, Conversation c " +
            "where c.id = m.conversationId and m.senderType = :senderType " +
            "and m.createdAt >= :from and m.createdAt < :to",
    )
    fun countActiveMembersBetween(
        @Param("senderType") senderType: SenderType,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): Long

    /** [commentStatus] 상태의 메시지 수. 모니터링 게이지(댓글 생성 적체)용 — 시각 조건 없이 현재 총량만 본다. */
    fun countByCommentStatus(commentStatus: CommentStatus): Long

    /** [status] 상태로 [before] 이전부터 머문 메시지 수. 대시보드의 '막힌 PENDING'(고아 생성) KPI. */
    @Query(
        "select count(m) from Message m " +
            "where m.commentStatus = :status and m.commentStatusUpdatedAt < :before",
    )
    fun countByCommentStatusOlderThan(
        @Param("status") status: CommentStatus,
        @Param("before") before: Instant,
    ): Long

    /** [from] 이후 작성된 메시지의 작성시각. 대시보드 일별 메시지 추이 집계용(앱에서 KST 날짜로 묶는다). */
    @Query("select m.createdAt from Message m where m.createdAt >= :from")
    fun findCreatedAtsSince(
        @Param("from") from: Instant,
    ): List<Instant>
}
