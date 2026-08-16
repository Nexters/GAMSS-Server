package com.nexters.gamss.notification.domain

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 푸시를 받을 기기 하나. 회원 1명이 여러 기기를 가질 수 있어 `member_id` 가 아니라 [token] 이
 * 유일하다 — 같은 기기가 두 회원에게 동시에 묶이면 이전 사용자에게 남의 알림이 간다.
 *
 * 행이 있다는 것 자체가 수신 동의다(V31 주석 참고). 그래서 수신 여부 플래그가 따로 없고,
 * 알림을 끄는 것은 곧 행을 지우는 것이다.
 *
 * 등록 경로는 [com.nexters.gamss.notification.repository.DeviceTokenRepository.upsert] 한 문장이라
 * 이 엔티티로 새 행을 저장하는 곳은 없다. 이 클래스는 조회·삭제와 테스트 데이터 준비에 쓰인다.
 */
@Entity
@Table(
    name = "device_tokens",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_device_tokens_token", columnNames = ["token"]),
    ],
)
class DeviceToken(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Embedded
    val token: FcmToken,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    fun isOwnedBy(memberId: Long): Boolean = this.memberId == memberId
}
