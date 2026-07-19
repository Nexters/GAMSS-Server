package com.nexters.gamss.conversation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * 채팅방. 사용자가 감정을 남기고 캐릭터가 반응하는 대화 세션 하나를 나타내며,
 * 말풍선(발화)은 [Message]로 저장된다. 회원은 memberId(ID 참조)로 연결한다.
 */
@Entity
@Table(name = "conversations")
@EntityListeners(AuditingEntityListener::class)
class Conversation(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    fun isOwnedBy(memberId: Long): Boolean = this.memberId == memberId
}
