package com.nexters.gamss.quality.domain

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 자동 채점 설정(단일 행). 채점 모델과 프롬프트는 llm_settings 의 QUALITY_JUDGE 행에 있다. */
@Entity
@Table(name = "quality_judge_setting")
class QualityJudgeSetting(
    @Id
    val id: Long = SINGLETON_ID,
    autoEnabled: Boolean = false,
    voteCount: Int = DEFAULT_VOTE_COUNT,
) {
    @Column(name = "auto_enabled", nullable = false)
    var autoEnabled: Boolean = autoEnabled
        protected set

    @Column(name = "vote_count", nullable = false)
    var voteCount: Int = voteCount
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    fun update(
        autoEnabled: Boolean,
        voteCount: Int,
    ) {
        if (voteCount !in MIN_VOTE_COUNT..MAX_VOTE_COUNT) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "채점 횟수는 $MIN_VOTE_COUNT~$MAX_VOTE_COUNT 사이여야 합니다.")
        }
        this.autoEnabled = autoEnabled
        this.voteCount = voteCount
        this.updatedAt = Instant.now()
    }

    companion object {
        const val SINGLETON_ID = 1L
        const val DEFAULT_VOTE_COUNT = 3
        const val MIN_VOTE_COUNT = 1
        const val MAX_VOTE_COUNT = 5
    }
}
