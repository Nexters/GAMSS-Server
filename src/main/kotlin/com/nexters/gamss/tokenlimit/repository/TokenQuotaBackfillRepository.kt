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
 * 기동 1회 백필 전용 쿼리. 평소 경로([TokenQuotaUsageRepository])와 갈라 둔 것은 **여기만
 * `generation_log` 를 읽기** 때문이다 - 한도 판정을 관측 테이블에서 떼어낸 것이 이번 작업의
 * 성과인데, 그 의존이 평소 경로에 섞여 들어오면 금방 되돌아간다.
 */
@Repository
interface TokenQuotaBackfillRepository : JpaRepository<TokenQuotaUsage, TokenQuotaUsageId> {
    /**
     * 매핑은 있는데 쿼터 행이 없는 주체에, [windowStart] 이후 `generation_log` 합을 채운다.
     *
     * **이미 있는 행은 건드리지 않는다**(`used_tokens = used_tokens`). 덮어쓰면 기동마다 카운터가
     * generation_log 기준으로 재계산돼, 적립이 성공했지만 관측 기록이 실패한 만큼이 사라진다 -
     * 그러면 사실상 관측 테이블이 진실의 원천으로 되돌아간다. 구멍만 메우고 손은 대지 않는다.
     *
     * 카드는 제외한다. 예전 합산 쿼리와 같은 기준이어야 백필이 없던 사용량을 만들어내지 않는다
     * ([com.nexters.gamss.card.service.CardService] 가 적립하지 않는 이유와 같다).
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
                "and g.generation_type not in ('CARD', 'CARD_EMOTION') " +
                "group by i.subject_key " +
                "on duplicate key update used_tokens = used_tokens",
        nativeQuery = true,
    )
    fun seedCurrentWindow(
        @Param("windowStart") windowStart: Instant,
    ): Int
}
