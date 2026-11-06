package com.nexters.gamss.tokenlimit.repository

import com.nexters.gamss.tokenlimit.domain.TokenQuotaUsage
import com.nexters.gamss.tokenlimit.domain.TokenQuotaUsageId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface TokenQuotaUsageRepository : JpaRepository<TokenQuotaUsage, TokenQuotaUsageId> {
    /** 이 회원이 속한 주체의 사용량. 매핑이나 사용량 행이 없으면 0 이다. */
    @Query(
        value =
            "select coalesce(sum(u.used_tokens), 0) from member_social_identity i " +
                "left join token_quota_usage u " +
                "on u.subject_key = i.subject_key and u.window_start = :windowStart " +
                "where i.member_id = :memberId",
        nativeQuery = true,
    )
    fun findUsedTokens(
        @Param("memberId") memberId: Long,
        @Param("windowStart") windowStart: Instant,
    ): Long

    /**
     * 이 회원이 속한 주체의 사용량에 [delta] 를 더한다.
     *
     * 엔티티를 읽어 고치지 않는다 - 동시 생성 두 건이 같은 값을 읽으면 한쪽 증가분이 사라진다.
     * 주체를 매핑에서 찾으므로 매핑이 없으면 0 행이 되고, 부르는 쪽이 그것을 실패로 남긴다.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(
        value =
            "insert into token_quota_usage (subject_key, window_start, used_tokens, updated_at) " +
                "select i.subject_key, :windowStart, :delta, now(6) from member_social_identity i " +
                "where i.member_id = :memberId " +
                "on duplicate key update used_tokens = used_tokens + :delta, updated_at = now(6)",
        nativeQuery = true,
    )
    fun addUsedTokens(
        @Param("memberId") memberId: Long,
        @Param("windowStart") windowStart: Instant,
        @Param("delta") delta: Long,
    ): Int

    /**
     * 방금 적립한 사용량을 읽는다. 잠금 읽기로 두는 이유는 트랜잭션 스냅샷 때문이다 - 일반 조회는 앞서 읽은
     * 시점을 볼 수 있어, 같은 주체의 앞선 생성이 커밋한 사용량을 놓치면 유료 차감이 덜 된다.
     */
    @Query(
        value = "select used_tokens from token_quota_usage where subject_key = :subjectKey and window_start = :windowStart for update",
        nativeQuery = true,
    )
    fun findUsedTokensForUpdate(
        @Param("subjectKey") subjectKey: String,
        @Param("windowStart") windowStart: Instant,
    ): Long?

    /** 보관 기간이 지난 구간을 지운다. */
    @Transactional
    @Modifying
    fun deleteByWindowStartBefore(windowStart: Instant): Int
}
