package com.nexters.gamss.monitoring.repository

import com.nexters.gamss.monitoring.domain.GenerationLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface GenerationLogRepository : JpaRepository<GenerationLog, Long> {
    /** [from] 이후의 생성 로그를 시간순으로 조회한다. 대시보드 품질 지표 집계에 쓴다. */
    @Query("select g from GenerationLog g where g.createdAt >= :from order by g.createdAt asc")
    fun findAllSince(
        @Param("from") from: Instant,
    ): List<GenerationLog>

    /**
     * [memberId]가 [since] 이후 소비한 총 토큰(used_tokens 합). 유저별 일일 상한 판정에 쓴다.
     * 성공·실패 무관하게 실제 과금된 토큰을 모두 세며(실패도 호출됐으면 과금됨), 값이 없는 행은 SUM에서 제외된다.
     */
    @Query(
        "select coalesce(sum(g.usedTokens), 0) from GenerationLog g " +
            "where g.memberId = :memberId and g.createdAt >= :since",
    )
    fun sumUsedTokensByMemberSince(
        @Param("memberId") memberId: Long,
        @Param("since") since: Instant,
    ): Long

    /**
     * 여러 대화방의 생성 로그를 한 번에 조회한다(백오피스 대화방 사용량·비용 페이지). 대시보드처럼 행을
     * 그대로 받아 앱단에서 대화방별 토큰 합·비용(모델별 단가)을 계산한다. conversation_id가 NULL인 과거 로그는 제외된다.
     */
    fun findByConversationIdIn(conversationIds: Collection<Long>): List<GenerationLog>
}
