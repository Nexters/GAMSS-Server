package com.nexters.gamss.llm.settings
import com.nexters.gamss.llm.prompt.PromptType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * 운영 중 백오피스에서 바꾸는 LLM 생성 설정(모델·시스템 프롬프트). [promptType]당 행이 최대 1개다
 * (unique 제약, V6 마이그레이션 참고). 행이 없으면 코드 기본값(GeminiProperties.model /
 * PromptProvider의 타입별 프롬프트)을 쓴다 — [LlmSettingsService] 참고.
 */
@Entity
@Table(name = "llm_settings")
@EntityListeners(AuditingEntityListener::class)
class LlmSettings(
    @Enumerated(EnumType.STRING)
    @Column(name = "prompt_type", length = 20, nullable = false, updatable = false)
    val promptType: PromptType,
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
