package com.nexters.gamss.admin.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * 백오피스 접근이 허용된 관리자 계정. 식별자는 정규화된 이메일(공백 제거·소문자)이며 중복은 UNIQUE로 막는다.
 * `ADMIN_EMAILS` 환경 부트스트랩과 합집합으로 허용 여부를 판정한다([com.nexters.gamss.admin.service.AdminAccountService]).
 * [createdByEmail]은 이 관리자를 추가한 관리자(감사용).
 */
@Entity
@Table(name = "admin_accounts")
@EntityListeners(AuditingEntityListener::class)
class AdminAccount(
    @Column(name = "email", length = 255, nullable = false)
    val email: String,
    @Column(name = "created_by_email", length = 255)
    val createdByEmail: String? = null,
) {
    init {
        require(email.isNotBlank()) { "관리자 이메일은 비어 있을 수 없습니다." }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set
}
