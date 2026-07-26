package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
}
