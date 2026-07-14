package com.nexters.gamss.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 회원의 소셜 로그인 계정. (provider, providerId)로 유일하며, 회원은 memberId(ID 참조)로 연결한다.
 * provider 는 문자열로 저장한다 — 제공자가 추가돼도 이 도메인 코드는 바뀌지 않는다.
 */
@Entity
@Table(
    name = "social_accounts",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_social_provider", columnNames = ["provider", "provider_id"]),
    ],
)
class SocialAccount(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Column(name = "provider", nullable = false, length = 30)
    val provider: String,
    @Column(name = "provider_id", nullable = false)
    val providerId: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
