package com.nexters.gamss.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * 서비스 사용자. 인증 방식(소셜 등)에 의존하지 않는 순수 유저 도메인.
 * 탈퇴는 소프트 삭제(WITHDRAWN 전이 + deletedAt 기록)로 처리한다.
 */
@Entity
@Table(name = "members")
@EntityListeners(AuditingEntityListener::class)
class Member(
    @Column(name = "email")
    var email: String? = null,
    @Embedded
    var nickname: Nickname? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    var status: MemberStatus = MemberStatus.ACTIVE
        protected set

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    fun updateEmail(email: String?) {
        this.email = email
    }

    fun updateNickname(nickname: Nickname) {
        this.nickname = nickname
    }

    fun withdraw() {
        status = MemberStatus.WITHDRAWN
        deletedAt = Instant.now()
    }

    fun isWithdrawn(): Boolean = status == MemberStatus.WITHDRAWN
}
