package com.nexters.gamss.quality.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 관리자가 백오피스에서 대화 원문을 연 기록. */
@Entity
@Table(name = "conversation_view_log")
class ConversationViewLog(
    @Column(name = "admin_email", nullable = false, updatable = false)
    val adminEmail: String,
    @Column(name = "conversation_id", nullable = false, updatable = false)
    val conversationId: Long,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "viewed_at", nullable = false, updatable = false)
    val viewedAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
