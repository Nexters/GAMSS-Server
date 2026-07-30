package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Optional

interface ConversationRepository : JpaRepository<Conversation, Long> {
    /** [start, end) 사이에 생성된 회원의 채팅방을 생성순으로 조회한다. 삭제된 채팅방은 제외한다. */
    @Query(
        "select c from Conversation c " +
            "where c.memberId = :memberId and c.createdAt >= :start and c.createdAt < :end " +
            "and c.status <> :excludedStatus " +
            "order by c.id asc",
    )
    fun findAllByMemberIdAndCreatedAtInRange(
        @Param("memberId") memberId: Long,
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("excludedStatus") excludedStatus: ConversationStatus = ConversationStatus.DELETED,
    ): List<Conversation>

    /**
     * 상태를 바꾸는 요청(메시지 저장·종료·삭제)에서 사용한다. 행을 잠가 다른 상태 변경 요청이
     * 커밋될 때까지 대기하게 만들어, 삭제 이후 작업 차단 계약이 경합으로 깨지지 않도록 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Optional<Conversation>

    /** [from, to) 사이 생성된 대화방 수. 대시보드의 '오늘 시작한 대화' KPI. */
    @Query("select count(c) from Conversation c where c.createdAt >= :from and c.createdAt < :to")
    fun countCreatedBetween(
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): Long

    /** [from] 이후 생성된 대화방의 생성시각. 대시보드 일별 추이 집계용(앱에서 KST 날짜로 묶는다). */
    @Query("select c.createdAt from Conversation c where c.createdAt >= :from")
    fun findCreatedAtsSince(
        @Param("from") from: Instant,
    ): List<Instant>

    /**
     * 카드 생성 시점에 받은 대화 전체 요약을 [id]에 원자적으로 저장한다. 행 잠금 없이 단일 컬럼만
     * 갱신해, 종료·삭제 같은 동시 상태 변경과 경합해도 엔티티 merge처럼 전체 행을 덮어쓰지 않는다.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Conversation c set c.summary = :summary where c.id = :id")
    fun updateSummary(
        @Param("id") id: Long,
        @Param("summary") summary: String,
    ): Int

    /**
     * [id]의 cardGenerationStatus가 [fromAny] 중 하나일 때만 [to]로 원자적으로 바꾼다
     * ([MessageRepository.updateCommentStatus]와 같은 CAS 패턴). 삭제된 채팅방은 제외해,
     * 종료 확인과 선점 사이에 다른 요청이 채팅방을 삭제해도 그 뒤로 선점되지 않게 한다.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
        "update Conversation c set c.cardGenerationStatus = :to, c.cardGenerationStatusUpdatedAt = :now " +
            "where c.id = :id and c.cardGenerationStatus in :fromAny and c.status <> :excludedStatus",
    )
    fun updateCardGenerationStatus(
        @Param("id") id: Long,
        @Param("to") to: CardGenerationStatus,
        @Param("fromAny") fromAny: List<CardGenerationStatus>,
        @Param("now") now: Instant,
        @Param("excludedStatus") excludedStatus: ConversationStatus = ConversationStatus.DELETED,
    ): Int

    /** 배포 중단·서버 크래시 등으로 [olderThan]보다 오래 PENDING에 머문 고아 카드 생성 상태를 NONE으로 되돌린다. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
        "update Conversation c set c.cardGenerationStatus = :to, " +
            "c.cardGenerationStatusUpdatedAt = null " +
            "where c.cardGenerationStatus = :from " +
            "and c.cardGenerationStatusUpdatedAt < :olderThan",
    )
    fun resetStaleCardGenerationPending(
        @Param("olderThan") olderThan: Instant,
        @Param("to") to: CardGenerationStatus = CardGenerationStatus.NONE,
        @Param("from") from: CardGenerationStatus = CardGenerationStatus.PENDING,
    ): Int
}
