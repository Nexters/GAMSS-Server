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
     * 회원의 **아직 진행 중인** 대화방을 최신순으로 조회한다(이어쓰기 목록).
     *
     * `status = ACTIVE` 하나로 "삭제되지 않았고 카드도 없는 방"이 모두 충족된다 —
     * 상태 전이가 `ACTIVE → ENDED → DELETED` 단방향이고([Conversation.end]·[Conversation.delete]
     * 외에 status 를 바꾸는 코드가 없다), 카드 생성은 ENDED 를 요구하므로
     * ([com.nexters.gamss.card.service.CardService] 의 claimForGeneration) **ACTIVE 방은 카드를
     * 가질 수 없다.** 그래서 cards 를 조인하지 않는다 — 걸러질 행이 없고, 읽는 사람에게
     * 'ACTIVE 인데 카드가 있을 수 있다'는 잘못된 인상만 준다.
     *
     * 종료한 방을 다시 열 수 있게 되면 이 전제가 깨지므로, 그때는 카드 존재 여부를 함께 봐야 한다.
     */
    @Query(
        "select c from Conversation c " +
            "where c.memberId = :memberId and c.status = :status " +
            "order by c.createdAt desc, c.id desc",
    )
    fun findAllInProgressByMemberId(
        @Param("memberId") memberId: Long,
        @Param("status") status: ConversationStatus = ConversationStatus.ACTIVE,
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
    @Modifying(flushAutomatically = true, clearAutomatically = true)
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
    @Modifying(flushAutomatically = true, clearAutomatically = true)
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
    @Modifying(flushAutomatically = true, clearAutomatically = true)
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

    /**
     * 같은 회원의 다른 채팅방 중 요약이 저장된 최근 [poolSize]개를 후보로 삼아, 그중 무작위로
     * [pickCount]개를 골라 반환한다(댓글 생성 시 과거 맥락으로 참고 — 매번 언급하지 않도록 일부만 뽑음).
     * 후보가 [pickCount]보다 적으면 있는 만큼만 반환한다. [excludeConversationId]는 현재 대화방(자기 자신) 제외용.
     */
    @Query(
        value =
            "select recent.summary from (" +
                "select summary from conversations " +
                "where member_id = :memberId and id <> :excludeConversationId " +
                "and summary is not null and status <> :excludedStatus " +
                "order by created_at desc, id desc limit :poolSize" +
                ") recent order by rand() limit :pickCount",
        nativeQuery = true,
    )
    fun findRandomPastSummaries(
        @Param("memberId") memberId: Long,
        @Param("excludeConversationId") excludeConversationId: Long,
        @Param("poolSize") poolSize: Int,
        @Param("pickCount") pickCount: Int,
        // native query라 엔티티의 @Enumerated(STRING) 매핑이 적용되지 않는다 — enum을 그대로 바인딩하면
        // Hibernate가 문자열이 아닌 다른 방식으로 바인딩해 이 필터가 무력화된다(Testcontainers 테스트로 확인).
        // 그래서 String으로 받되 값의 출처는 ConversationStatus.DELETED로 고정한다.
        @Param("excludedStatus") excludedStatus: String = ConversationStatus.DELETED.name,
    ): List<String>
}
