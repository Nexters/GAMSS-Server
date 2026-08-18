package com.nexters.gamss.card.repository

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import com.nexters.gamss.emotion.domain.EmotionType
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Optional

interface CardRepository : JpaRepository<Card, Long> {
    /**
     * 상태를 바꾸는 요청(삭제)에서 사용한다. 행을 잠가 다른 삭제 요청이 커밋될 때까지 대기시킨다 —
     * 잠금 없이 읽으면 동시 요청 둘이 모두 `deletedAt == null` 을 보고 둘 다 성공해, 두 번째가
     * CARD_ALREADY_DELETED 대신 200 을 받고 삭제 시각도 늦은 쪽으로 덮인다
     * ([com.nexters.gamss.conversation.repository.ConversationRepository.findByIdForUpdate] 와 같은 이유).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Card c where c.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Optional<Card>

    /**
     * 사용자에게 **보이는** 카드 한 장을 id 로 조회한다.
     *
     * 가시성 조건은 [findAllByMemberIdAndConversationCreatedAtInRange] 와 같다 — 이미 지운 카드와
     * 삭제된 채팅방의 카드는 제외한다. 두 조건이 갈라지면 캘린더에 없는 카드가 id 로는 열리는
     * 모순이 생기므로 **항상 함께 바뀌어야 한다**.
     *
     * 소유자를 조건에 넣지 않는다 — **보이는** 카드가 남의 것이면 '없음'(404)이 아니라
     * CARD_ACCESS_DENIED(403)로 구분해야 해서, 소유권은 조회한 뒤 서비스에서 판별한다
     * ([CardService.getCard]).
     *
     * 가시성을 먼저 거르므로 **남이 이미 지운 카드는 403 이 아니라 404** 가 된다. 단건 삭제
     * ([CardService.deleteCard])는 소유권을 먼저 봐서 같은 카드에 403 을 주므로 두 API 가
     * 갈린다 — 읽기에서까지 남의 삭제 여부를 알려줄 이유가 없어 이쪽을 택했다.
     */
    @Query(
        "select c from Card c, Conversation cv " +
            "where cv.id = c.conversationId and c.id = :id " +
            "and cv.status <> :excludedStatus and c.deletedAt is null",
    )
    fun findVisibleById(
        @Param("id") id: Long,
        @Param("excludedStatus") excludedStatus: ConversationStatus = ConversationStatus.DELETED,
    ): Optional<Card>

    /**
     * 카드 재생성 차단용. **삭제된 카드도 '존재'로 센다** — 카드 삭제는 되돌릴 수 없고
     * (conversation_id UNIQUE), 같은 대화방에 카드를 다시 만들 수 없어야 하기 때문이다.
     */
    fun existsByConversationId(conversationId: Long): Boolean

    /** 카드가 생성된 대화방 id들만 골라 반환한다(백오피스 대화방 사용량 페이지의 카드 생성 여부 배치 조회). */
    @Query("select c.conversationId from Card c where c.conversationId in :conversationIds")
    fun findConversationIdsIn(
        @Param("conversationIds") conversationIds: Collection<Long>,
    ): List<Long>

    /**
     * 회원의 특정 감정 카드 중 **일괄 삭제 대상**인 카드의 대화방 id 를 모은다.
     *
     * 대상은 **사용자가 볼 수 있는 카드**로 한정한다 — 이미 지운 카드(deletedAt)와 삭제된 채팅방의
     * 카드는 캘린더에 나타나지 않으므로 세지 않는다. 그래야 응답의 삭제 건수가 사용자가 화면에서
     * 본 장수와 일치한다([findAllByMemberIdAndConversationCreatedAtInRange] 와 같은 가시성 규칙).
     *
     * 카드를 바로 UPDATE 하지 않고 id 부터 모으는 이유는 대화방도 함께 지워야 하기 때문이다.
     * 카드를 먼저 지우면 `deletedAt is null` 이 깨져 대화방을 찾을 수 없고, 대화방을 먼저 지우면
     * `status <> DELETED` 가 깨져 카드를 찾을 수 없다. 두 UPDATE 가 같은 대상을 보게 하려면
     * 대상 집합을 먼저 확정해야 한다([CardService.deleteCardsWithConversations]).
     */
    @Query(
        "select c.conversationId from Card c, Conversation cv " +
            "where cv.id = c.conversationId and c.memberId = :memberId and c.emotion = :emotion " +
            "and c.deletedAt is null and cv.status <> :excludedStatus",
    )
    fun findDeletableConversationIdsByEmotion(
        @Param("memberId") memberId: Long,
        @Param("emotion") emotion: EmotionType,
        @Param("excludedStatus") excludedStatus: ConversationStatus = ConversationStatus.DELETED,
    ): List<Long>

    /**
     * 지정한 대화방들의 카드를 한 번에 삭제하고 삭제 건수를 돌려준다.
     *
     * 행 잠금이 필요 없다 — `deletedAt is null` 조건을 건 단일 UPDATE 라 동시 요청이 와도
     * 뒤늦은 쪽은 0건을 갱신하고 끝난다.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Card c set c.deletedAt = :now where c.conversationId in :conversationIds and c.deletedAt is null")
    fun softDeleteByConversationIds(
        @Param("conversationIds") conversationIds: Collection<Long>,
        @Param("now") now: Instant,
    ): Int

    /**
     * 회원의 카드 중 **전체 삭제 대상**인 카드의 대화방 id 를 모은다.
     *
     * 감정 조건만 없을 뿐 [findDeletableConversationIdsByEmotion] 과 **같은 가시성 규칙**을 쓴다 —
     * 이미 지운 카드와 삭제된 채팅방의 카드는 세지 않는다. 두 쿼리와 조회 쿼리
     * ([findAllByMemberIdAndConversationCreatedAtInRange])의 가시성 조건은 항상 함께 바뀌어야 한다.
     */
    @Query(
        "select c.conversationId from Card c, Conversation cv " +
            "where cv.id = c.conversationId and c.memberId = :memberId " +
            "and c.deletedAt is null and cv.status <> :excludedStatus",
    )
    fun findDeletableConversationIds(
        @Param("memberId") memberId: Long,
        @Param("excludedStatus") excludedStatus: ConversationStatus = ConversationStatus.DELETED,
    ): List<Long>

    /**
     * [start, end) 사이(대화 생성시간 기준)에 속한 회원의 카드를 오래된 순으로 조회한다.
     * 삭제된 채팅방의 카드는 제외한다 — 카드는 대화 종료 여부와 무관하게 삭제될 수 있어(Conversation.delete),
     * 삭제 후에도 남은 카드가 캘린더·날짜별 조회에 계속 나타나는 걸 막는다.
     * 사용자가 직접 지운 카드(deletedAt)도 제외한다.
     *
     * 이 필터는 **사용자 조회에만** 적용한다 — 백오피스 지표(countCreatedBetween·countByEmotionSince·
     * findCreatedAtsSince)는 생성 이력이라 사용자 삭제로 소급 변동하면 추이가 왜곡된다.
     *
     * 감정으로 한 번 더 거르는 [findAllByMemberIdAndEmotionAndConversationCreatedAtInRange] 도 같은
     * 가시성 규칙을 쓴다 — 함께 바꿔야 하는 쿼리가 하나 더 있다는 뜻이다.
     */
    @Query(
        "select c from Card c, Conversation cv " +
            "where cv.id = c.conversationId and c.memberId = :memberId " +
            "and c.conversationCreatedAt >= :start and c.conversationCreatedAt < :end " +
            "and cv.status <> :excludedStatus " +
            "and c.deletedAt is null " +
            "order by c.conversationCreatedAt asc, c.id asc",
    )
    fun findAllByMemberIdAndConversationCreatedAtInRange(
        @Param("memberId") memberId: Long,
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("excludedStatus") excludedStatus: ConversationStatus = ConversationStatus.DELETED,
    ): List<Card>

    /**
     * [start, end) 사이(대화 생성시간 기준)에 속한 회원의 카드 중 [emotion] 인 것만 **최신순**으로 조회한다.
     *
     * 가시성 조건은 [findAllByMemberIdAndConversationCreatedAtInRange] 와 같다 — 조회·삭제 쿼리들이
     * 공유하는 규칙이라 **항상 함께 바뀌어야 한다**. 여기서 갈리면 캘린더에는 없는 카드가 감정 탭에서만
     * 보이는 모순이 생긴다.
     *
     * **정렬만 다르다.** 한 달치를 몰아 보는 목록이라 최근 기록이 위에 오는 편이 자연스럽고, 클라이언트가
     * 응답을 재정렬하지 않아 서버가 내보내는 순서가 곧 화면 순서이기 때문이다. 날짜별·캘린더 조회가
     * 오름차순인 것은 하루치이거나 날짜 칸에 꽂는 데이터라 배열 순서가 화면에 드러나지 않아서다.
     *
     * id 타이브레이크도 같이 뒤집는다 — `conversation_created_at` 이 `DATETIME(6)` 이라 동률이 가능한데
     * `desc, id asc` 로 두면 동률 구간만 순서가 어긋난다.
     */
    @Query(
        "select c from Card c, Conversation cv " +
            "where cv.id = c.conversationId and c.memberId = :memberId and c.emotion = :emotion " +
            "and c.conversationCreatedAt >= :start and c.conversationCreatedAt < :end " +
            "and cv.status <> :excludedStatus " +
            "and c.deletedAt is null " +
            "order by c.conversationCreatedAt desc, c.id desc",
    )
    fun findAllByMemberIdAndEmotionAndConversationCreatedAtInRange(
        @Param("memberId") memberId: Long,
        @Param("emotion") emotion: EmotionType,
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
