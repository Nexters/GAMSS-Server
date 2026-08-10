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
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * 프롬프트 편집 이력 한 건. append-only라 모든 필드가 불변이다 — 이력이 고쳐질 수 있으면
 * 감사 기록으로서의 가치가 없다. [version]은 [promptType] 안에서 1부터 증가한다(unique 제약).
 *
 * [savedBy]가 null이면 관리자가 아니라 시스템이 남긴 기록이다(V22 시딩 등).
 * [restoredFromVersion]이 있으면 이 저장이 해당 버전의 복원이라는 뜻이다.
 */
@Entity
@Table(name = "prompt_revisions")
@EntityListeners(AuditingEntityListener::class)
class PromptRevision(
    @Enumerated(EnumType.STRING)
    @Column(name = "prompt_type", length = 20, nullable = false, updatable = false)
    val promptType: PromptType,
    @Column(name = "version", nullable = false, updatable = false)
    val version: Int,
    @Column(name = "system_prompt", nullable = false, updatable = false, columnDefinition = "TEXT")
    val systemPrompt: String,
    @Column(name = "saved_by", updatable = false)
    val savedBy: String?,
    @Column(name = "restored_from_version", updatable = false)
    val restoredFromVersion: Int? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set
}
