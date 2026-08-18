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
     * 회원의 대화방을 상태로 걸러 최신순으로 조회한다. **어떤 상태가 무슨 의미인지는 호출자가 정한다** —
     * 여기서는 걸러낸다는 사실만 안다.
     *
     * `createdAt` 만으로 정렬하지 않는다 — `DATETIME(6)` 이라 한 요청 안에서 연달아 만든 방이 같은
     * 마이크로초를 가질 수 있고, 그러면 순서가 실행마다 흔들린다. id 로 타이브레이크한다.
     */
    @Query(
        "select c from Conversation c " +
            "where c.memberId = :memberId and c.status = :status " +
            "order by c.createdAt desc, c.id desc",
    )
    fun findAllByMemberIdAndStatus(
        @Param("memberId") memberId: Long,
        @Param("status") status: ConversationStatus,
    ): List<Conversation>

    /**
     * 자동 종료·카드 생성 배치의 대상 id를 조회한다 — [createdAfter] 이후 [createdBefore](가장 최근에
     * 지난 하루 경계, KST 05시) 전에 만들어졌고, 삭제되지 않았으며, 아직 카드가 없는
     * (`cardGenerationStatus <> DONE`) 방.
     *
     * 상한을 "어제 하루"로 좁히지 않는다 — 배치가 하루 걸러 실패하면 그날 방들이 영영 카드 없이
     * 남는다. 이미 종료된 방까지 포함하는 것도 같은 이유로, 종료까지만 되고 카드 생성에서 끊긴 방이
     * 다음 실행에서 이어서 처리된다. DONE 필터는 불필요한 일감을 줄이는 용도일 뿐이고, 중복 카드
     * 생성을 실제로 막는 것은 카드 생성 경로의 CAS 선점과 `cards.conversation_id` 유니크 제약이다.
     *
     * [createdAfter] 하한은 요약 저장([Conversation.updateSummary])이 배포되기 전에 만들어진 방을
     * 걸러낸다. 그 방들은 요약이 없어 카드를 만들 수 없으므로, 하한이 없으면 첫 실행이 기존
     * 사용자들의 진행 중인 방을 전부 카드 없이 종료해버린다
     * ([com.nexters.gamss.card.config.CardProperties.autoCardStartDate]).
     *
     * **하한은 고정이고 상한만 매일 밀리므로, 끝나지 않는 방은 대상에 계속 쌓인다.** 그래서 결론이
     * 바뀔 수 없는 방과 바뀔 수 있는 방을 다르게 다룬다:
     * - 요약이 없어 포기한 방([CardGenerationStatus.SKIPPED])은 종료된 방이라 요약이 채워질 길이
     *   없다 — DONE과 함께 아예 제외한다.
     * - 실패한 방([CardGenerationStatus.FAILED])은 재시도가 살아 있어야 하므로 상태로 뺄 수 없다.
     *   대신 마지막 시도가 이번 하루 안이면 건너뛰어 **하루 한 번**으로 제한한다. 배치 카드는
     *   감정 분류·한 줄 생성 2회를 부르므로, 영구적으로 실패하는 방이 생겨도 태우는 양이 예측
     *   가능한 선에서 묶인다.
     */
    @Query(
        "select c.id from Conversation c " +
            "where c.createdAt >= :createdAfter and c.createdAt < :createdBefore " +
            "and c.status <> :deletedStatus and c.cardGenerationStatus not in :finishedStatuses " +
            "and (c.cardGenerationStatus <> :failedStatus " +
            "or c.cardGenerationStatusUpdatedAt is null or c.cardGenerationStatusUpdatedAt < :createdBefore) " +
            "order by c.id asc",
    )
    fun findAutoCardTargetIds(
        @Param("createdAfter") createdAfter: Instant,
        @Param("createdBefore") createdBefore: Instant,
        @Param("deletedStatus") deletedStatus: ConversationStatus = ConversationStatus.DELETED,
        @Param("finishedStatuses") finishedStatuses: List<CardGenerationStatus> =
            listOf(CardGenerationStatus.DONE, CardGenerationStatus.SKIPPED),
        @Param("failedStatus") failedStatus: CardGenerationStatus = CardGenerationStatus.FAILED,
    ): List<Long>

    /**
     * 아직 **미종료** 상태인 방을 가진 회원들. 04:30 리마인더가 "곧 자동으로 닫힌다"고 알릴 대상이다.
     *
     * [findAutoCardTargetIds] 를 재사용하면 안 된다. 그 쿼리는 `cardGenerationStatus` 기준이라
     * **이미 종료됐는데 카드만 없는 방**까지 포함한다 — 직접 마무리한 사람에게 "마무리하세요"가 간다.
     * 여기서는 `status = ACTIVE` 인 방만 본다.
     *
     * 기간은 5시 배치와 같은 창을 쓴다([com.nexters.gamss.card.service.AutoCardWindow]). 하한 밖의
     * 방은 배치가 손대지 않으므로, 알려봐야 닫히지도 않는다.
     *
     * 회원당 한 번만 알리므로 방이 아니라 **회원 id** 를 중복 없이 돌려준다. 탈퇴 회원은 따로 거르지
     * 않는다 — 탈퇴하면 기기 토큰이 함께 지워져(DeviceTokenCleaner) 보낼 대상이 애초에 없다.
     */
    @Query(
        "select distinct c.memberId from Conversation c " +
            "where c.status = :activeStatus " +
            "and c.createdAt >= :createdAfter and c.createdAt < :createdBefore",
    )
    fun findMemberIdsWithUnfinishedConversations(
        @Param("createdAfter") createdAfter: Instant,
        @Param("createdBefore") createdBefore: Instant,
        @Param("activeStatus") activeStatus: ConversationStatus = ConversationStatus.ACTIVE,
    ): List<Long>

    /**
     * 상태를 바꾸는 요청(메시지 저장·종료·삭제)에서 사용한다. 행을 잠가 다른 상태 변경 요청이
     * 커밋될 때까지 대기하게 만들어, 삭제 이후 작업 차단 계약이 경합으로 깨지지 않도록 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Optional<Conversation>

    /**
     * 주어진 id 중 **이 회원의, 아직 살아 있는** 채팅방 id만 추린다. 일괄 삭제가 실제로 지울 대상을
     * 정할 때 쓴다.
     *
     * 남의 방·없는 방·이미 지운 방은 조용히 빠진다 — 단건 삭제처럼 403·404·409로 전체를 거절하면
     * id 하나만 잘못 섞여도 나머지를 못 지우고, 남의 방이 존재하는지도 응답으로 드러난다.
     */
    @Query(
        "select c.id from Conversation c " +
            "where c.memberId = :memberId and c.id in :ids and c.status <> :deletedStatus",
    )
    fun findDeletableIds(
        @Param("memberId") memberId: Long,
        @Param("ids") ids: Collection<Long>,
        @Param("deletedStatus") deletedStatus: ConversationStatus = ConversationStatus.DELETED,
    ): List<Long>

    /**
     * 지정한 채팅방들을 한 번에 삭제한다(soft delete). 카드 일괄 삭제가 대화방까지 지울 때 쓴다
     * ([com.nexters.gamss.card.service.CardService.deleteCardsWithConversations]).
     *
     * 이미 삭제된 방은 건너뛴다. 단건 삭제([Conversation.delete])와 달리 예외를 던지지 않는다 —
     * 일괄 삭제는 대상이 없어도 성공해야 하고, 여기서 막히면 나머지 방까지 못 지운다.
     *
     * [Conversation.updatedAt] 을 직접 갱신한다 — 벌크 UPDATE 는 엔티티를 거치지 않아
     * `@LastModifiedDate` 감사 리스너가 돌지 않는다. 넣지 않으면 같은 '채팅방 삭제'인데
     * 단건 경로만 변경 시각이 남아 두 경로가 서로 다른 데이터를 만든다.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update Conversation c set c.status = :deletedStatus, c.updatedAt = :now " +
            "where c.id in :ids and c.status <> :deletedStatus",
    )
    fun softDeleteByIds(
        @Param("ids") ids: Collection<Long>,
        @Param("now") now: Instant,
        @Param("deletedStatus") deletedStatus: ConversationStatus = ConversationStatus.DELETED,
    ): Int

    /** [status] 상태의 대화방 수. 모니터링 게이지(진행 중 대화 수)용 — 시점 스냅샷이라 인덱스만 탄다. */
    fun countByStatus(status: ConversationStatus): Long

    /**
     * [cardGenerationStatus] 상태의 대화방 수. 모니터링 게이지(카드 생성 적체·실패 누적)용.
     *
     * 삭제된 방은 뺀다 — 소프트 삭제는 status 만 DELETED 로 바꾸고 card_generation_status 는
     * 그대로 두기 때문에, 세지 않으면 처리할 수 없는 적체가 게이지에 영원히 남는다.
     */
    @Query(
        "select count(c) from Conversation c " +
            "where c.cardGenerationStatus = :cardGenerationStatus and c.status <> :excludedStatus",
    )
    fun countByCardGenerationStatus(
        @Param("cardGenerationStatus") cardGenerationStatus: CardGenerationStatus,
        @Param("excludedStatus") excludedStatus: ConversationStatus = ConversationStatus.DELETED,
    ): Long

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
     * 같은 회원의 **종료된** 다른 채팅방 중 요약이 저장된 최근 [poolSize]개를 후보로 삼아, 그중 무작위로
     * [pickCount]개를 골라 반환한다(댓글 생성 시 과거 맥락으로 참고 — 매번 언급하지 않도록 일부만 뽑음).
     * 후보가 [pickCount]보다 적으면 있는 만큼만 반환한다. [excludeConversationId]는 현재 대화방(자기 자신) 제외용.
     *
     * 종료 여부를 status로 직접 거른다 — 요약이 카드 생성 시점에만 저장되던 때는 `summary is not null`이
     * 곧 "끝난 방"을 뜻했지만, 이제 진행 중에도 임시 요약이 저장되므로([Conversation.updateSummary])
     * 그 조건만으로는 아직 쓰는 중인 오늘의 대화방까지 과거 맥락에 섞인다.
     */
    @Query(
        value =
            "select recent.summary from (" +
                "select summary from conversations " +
                "where member_id = :memberId and id <> :excludeConversationId " +
                "and summary is not null and status = :endedStatus " +
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
        // 그래서 String으로 받되 값의 출처는 ConversationStatus.ENDED로 고정한다.
        @Param("endedStatus") endedStatus: String = ConversationStatus.ENDED.name,
    ): List<String>
}
