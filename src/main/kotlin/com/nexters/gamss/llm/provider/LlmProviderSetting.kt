package com.nexters.gamss.llm.provider

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/** 지금 쓰는 Gemini 호출 경로. 앱 전체 단일 행(V37 시드)이고 백오피스에서 바꾼다. */
@Entity
@Table(name = "llm_provider_setting")
@EntityListeners(AuditingEntityListener::class)
class LlmProviderSetting(
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20, nullable = false)
    var provider: LlmProvider,
) {
    /** 단일 행이라 채번하지 않는다. DB 가 id = 1 을 강제한다(V37). */
    @Id
    val id: Long = SINGLE_ROW_ID

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    fun update(provider: LlmProvider) {
        this.provider = provider
    }

    private companion object {
        const val SINGLE_ROW_ID = 1L
    }
}
