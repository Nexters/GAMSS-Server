package com.nexters.gamss.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 회원별 현재 유효한 리프레시 토큰. 재발급 시 회전(rotate)한다.
 */
@Entity
@Table(
    name = "refresh_tokens",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_refresh_member", columnNames = ["member_id"]),
    ],
)
class RefreshToken(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Column(name = "token", nullable = false, length = 512)
    var token: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    fun rotate(token: String) {
        this.token = token
    }

    fun matches(token: String): Boolean = this.token == token
}
