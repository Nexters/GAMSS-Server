package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.CommentStatus
import com.nexters.gamss.conversation.domain.Message
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface MessageRepository : JpaRepository<Message, Long> {
    fun findAllByConversationIdOrderByIdAsc(conversationId: Long): List<Message>

    /** 특정 일기(root) 메시지에 달린 캐릭터 댓글·티키타카 전체를 작성순으로 조회한다. */
    fun findAllByRootMessageIdOrderByIdAsc(rootMessageId: Long): List<Message>

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
        "update Message m set m.commentStatus = com.nexters.gamss.conversation.domain.CommentStatus.NONE, " +
            "m.commentStatusUpdatedAt = null " +
            "where m.commentStatus = com.nexters.gamss.conversation.domain.CommentStatus.PENDING " +
            "and m.commentStatusUpdatedAt < :olderThan",
    )
    fun resetStalePending(
        @Param("olderThan") olderThan: Instant,
    ): Int
}
