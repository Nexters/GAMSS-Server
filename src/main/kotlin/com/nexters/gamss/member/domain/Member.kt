package com.nexters.gamss.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 서비스 사용자. 인증 방식(소셜 등)에 의존하지 않는 순수 유저 도메인.
 */
@Entity
@Table(name = "members")
class Member(
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
