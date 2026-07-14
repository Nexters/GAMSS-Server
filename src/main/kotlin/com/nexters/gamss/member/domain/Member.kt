package com.nexters.gamss.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 소셜 로그인으로 식별되는 회원. (provider, providerId)가 유일 식별자.
 */
@Entity
@Table(
    name = "members",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_member_provider", columnNames = ["provider", "provider_id"]),
    ],
)
class Member(
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    val provider: OAuthProvider,
    @Column(name = "provider_id", nullable = false)
    val providerId: String,
    @Column(name = "email")
    var email: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    fun updateEmail(email: String?) {
        this.email = email
    }
}
