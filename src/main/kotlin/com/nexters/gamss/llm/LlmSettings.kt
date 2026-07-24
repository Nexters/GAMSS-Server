package com.nexters.gamss.llm

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
 * 운영 중 백오피스에서 바꾸는 LLM 생성 설정(모델·시스템 프롬프트). 단일 행만 유지한다.
 * 행이 없으면 코드 기본값(GeminiProperties.model / PromptProvider.systemPrompt)을 쓴다 —
 * [LlmSettingsService] 참고.
 */
@Entity
@Table(name = "llm_settings")
@EntityListeners(AuditingEntityListener::class)
class LlmSettings(
    @Column(name = "model", length = 100, nullable = false)
    var model: String,
    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    var systemPrompt: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    fun update(
        model: String,
        systemPrompt: String,
    ) {
        this.model = model
        this.systemPrompt = systemPrompt
    }
}
