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
 * **회원이 아니라 주체([subjectKey])에 귀속된다.** 탈퇴 후 재가입하면 회원은 새로 생기지만 소셜
 * 신원은 같으므로 같은 행에 계속 누적된다 - 탈퇴가 한도를 리셋하는 경로를 이렇게 끊는다.
 *
 * `generation_log` 와 값이 정확히 일치할 필요는 없다. 그쪽은 비용 분석용 관측 기록이고 이 테이블은
 * 집행용이다. 두 쓰기는 서로 독립이라([TokenQuotaRecorder]) 한쪽이 실패해도 다른 쪽은 남는다.
 *
 * 적립은 엔티티를 읽어 고치지 않고 upsert 한 문장으로 한다
 * ([com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository.addUsedTokens]) - 동시 생성에서
 * read-modify-write 가 겹치면 사용량이 덜 세어진다.
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
