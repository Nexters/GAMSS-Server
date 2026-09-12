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
    /**
     * 이 회원이 속한 주체의 [windowStart] 구간 사용량. 행이 없으면 0 이다.
     *
     * 회원 -> 주체 -> 사용량을 한 문장으로 잇는다. 매핑은 PK 조회, 사용량은 PK 조회라 재가입이 몇 번
     * 반복돼도 계획이 그대로다 - 회원별로 합산하던 방식은 대상 회원 수만큼 범위가 늘어난다.
     *
     * 매핑이 아직 없는 회원은 0 을 돌려준다([com.nexters.gamss.member.domain.MemberSocialIdentity] 가
     * 없는 경우). 한도를 못 세는 상태이므로 기동 백필이 그 구멍을 메운다
     * ([com.nexters.gamss.tokenlimit.service.TokenQuotaBackfill]).
     */
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
     * 이 회원이 속한 주체의 [windowStart] 구간 사용량에 [delta] 를 더한다.
     *
     * **엔티티를 읽어 고치지 않는다.** 읽고 더해 저장하면 동시 생성 두 건이 같은 값을 읽어 한쪽 증가분이
     * 사라진다. upsert 한 문장이면 DB 가 원자적으로 누적한다.
     *
     * `insert ... select` 인 것은 주체를 매핑에서 찾아야 하기 때문이다. 매핑이 없으면 삽입할 행이 없어
     * **조용히 아무 일도 일어나지 않는다** - 부르는 쪽이 0 을 보고 기록 실패로 남긴다
     * ([com.nexters.gamss.tokenlimit.service.TokenQuotaRecorder]).
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
     * 주체와 구간을 직접 지정해 적립한다. 회원 매핑을 거치지 않는 유일한 경로이며 기동 백필이 쓴다 -
     * 백필은 이미 주체별로 합을 계산해 둔 상태라 매핑을 한 번 더 거칠 이유가 없다.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(
        value =
            "insert into token_quota_usage (subject_key, window_start, used_tokens, updated_at) " +
                "values (:subjectKey, :windowStart, :usedTokens, now(6)) " +
                "on duplicate key update used_tokens = used_tokens + :usedTokens, updated_at = now(6)",
        nativeQuery = true,
    )
    fun addUsedTokensBySubject(
        @Param("subjectKey") subjectKey: String,
        @Param("windowStart") windowStart: Instant,
        @Param("usedTokens") usedTokens: Long,
    )

    /** 보관 기간이 지난 구간을 지운다. 집행이 끝난 구간의 사용량을 남겨둘 이유가 없다. */
    @Transactional
    @Modifying
    fun deleteByWindowStartBefore(windowStart: Instant): Int
}
