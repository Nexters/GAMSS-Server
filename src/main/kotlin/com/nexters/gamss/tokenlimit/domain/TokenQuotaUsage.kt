package com.nexters.gamss.tokenlimit.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * 쿼터 주체 하나가 한 구간에서 쓴 토큰. 일일 한도 판정의 단일 원천이다(#222).
 *
 * 회원이 아니라 주체([subjectKey])에 귀속되므로 탈퇴 후 재가입해도 같은 행에 누적된다.
 * `generation_log` 와 값이 정확히 일치할 필요는 없다 - 그쪽은 비용 분석용이고 이쪽은 집행용이라
 * 쓰기가 서로 독립이다([com.nexters.gamss.tokenlimit.service.TokenQuotaRecorder]).
 */
@Entity
@Table(name = "token_quota_usage")
@EntityListeners(AuditingEntityListener::class)
@IdClass(TokenQuotaUsageId::class)
class TokenQuotaUsage(
    @Id
    @Column(name = "subject_key", length = 64, nullable = false)
    val subjectKey: String,
    @Id
    @Column(name = "window_start", nullable = false)
    val windowStart: Instant,
    @Column(name = "used_tokens", nullable = false)
    val usedTokens: Long = 0,
) {
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set
}
