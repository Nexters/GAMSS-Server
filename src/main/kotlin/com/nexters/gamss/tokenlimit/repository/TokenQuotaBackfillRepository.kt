package com.nexters.gamss.tokenlimit.repository

import com.nexters.gamss.tokenlimit.domain.TokenQuotaUsage
import com.nexters.gamss.tokenlimit.domain.TokenQuotaUsageId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 기동 1회 백필 전용. 평소 경로([TokenQuotaUsageRepository])와 갈라 둔 것은 여기만 `generation_log`
 * 를 읽기 때문이다 - 판정을 관측 테이블에서 떼어낸 것이 이번 작업의 성과다.
 */
@Repository
interface TokenQuotaBackfillRepository : JpaRepository<TokenQuotaUsage, TokenQuotaUsageId> {
    /**
     * 쿼터 행이 없는 주체에 [windowStart] 이후 `generation_log` 합을 채운다.
     *
     * 이미 있는 행은 건드리지 않는다(`used_tokens = used_tokens`) - 덮어쓰면 기동마다 재계산돼
     * 관측 테이블이 사실상 진실의 원천으로 되돌아간다.
     *
     * 제외 목록은 적립 쪽과 같은 곳에서 받는다
     * ([com.nexters.gamss.monitoring.domain.GenerationType.namesNotCountingTowardQuota]).
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value =
            "insert into token_quota_usage (subject_key, window_start, used_tokens, updated_at) " +
                "select i.subject_key, :windowStart, sum(g.used_tokens), now(6) " +
                "from member_social_identity i " +
                "join generation_log g on g.member_id = i.member_id " +
                "where g.created_at >= :windowStart and g.used_tokens is not null " +
                "and g.generation_type not in (:excludedTypes) " +
                "group by i.subject_key " +
                "on duplicate key update used_tokens = used_tokens",
        nativeQuery = true,
    )
    fun seedCurrentWindow(
        @Param("windowStart") windowStart: Instant,
        @Param("excludedTypes") excludedTypes: Collection<String>,
    ): Int
}
