package com.nexters.gamss.tokenlimit.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * 유저별 일일 토큰 상한 정책. 앱 전체 단일 행(id=1, V15 시드)으로 존재하며 백오피스에서 값을 조절한다.
 * 상한을 실제로 적용할지 여부(배포 환경만)는 이 엔티티가 아니라 코드 설정(gamss.token-limit.enabled)이 정한다 —
 * 여기서는 '얼마나([dailyTokenLimit])'와 '언제 리셋([resetHour], KST 시각)'만 관리한다.
 */
@Entity
@Table(name = "token_policy")
@EntityListeners(AuditingEntityListener::class)
class TokenPolicy(
    @Column(name = "daily_token_limit", nullable = false)
    var dailyTokenLimit: Long,
    @Column(name = "reset_hour", nullable = false)
    var resetHour: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    fun update(
        dailyTokenLimit: Long,
        resetHour: Int,
    ) {
        require(dailyTokenLimit >= 0) { "일일 토큰 상한은 0 이상이어야 합니다." }
        require(resetHour in 0..23) { "리셋 시각은 0~23 사이여야 합니다." }
        this.dailyTokenLimit = dailyTokenLimit
        this.resetHour = resetHour
    }
}
