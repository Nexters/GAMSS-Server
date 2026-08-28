package com.nexters.gamss.notification.domain

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
 * 알림 한 건이 대화방 하나에 대해 어떻게 끝났는지 남긴 관측용 기록.
 *
 * 발송은 회원 단위이고 이 기록은 대화방 단위다. 발송 한 번이 방 여러 개를 커버하면 방마다 한 줄씩
 * 남고 [outcome] 에 같은 결과가 적힌다. 백오피스가 대화방별로 보여줘야 하기 때문이다.
 *
 * 한 번 남기면 바뀌지 않는다. 재발송은 새 줄이다.
 */
@Entity
@Table(name = "notification_log")
@EntityListeners(AuditingEntityListener::class)
class NotificationLog(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Column(name = "conversation_id", nullable = false)
    val conversationId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    val type: NotificationType,
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 20, nullable = false)
    val outcome: NotificationOutcome,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set
}
