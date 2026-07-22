package com.nexters.gamss.conversation.domain

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    var status: ConversationStatus = ConversationStatus.ACTIVE
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    fun isOwnedBy(memberId: Long): Boolean = this.memberId == memberId

    /** 채팅방을 종료한다. 이미 종료된 방을 다시 종료하면 예외를 던진다. */
    fun end() {
        if (status == ConversationStatus.ENDED) {
            throw BusinessException(ErrorCode.CONVERSATION_ALREADY_ENDED)
        }
        status = ConversationStatus.ENDED
    }

    /** 종료된 채팅방에는 사용자 메시지를 추가할 수 없다. */
    fun ensureActive() {
        if (status == ConversationStatus.ENDED) {
            throw BusinessException(ErrorCode.CONVERSATION_ENDED)
        }
    }

    fun createMessage(
        senderType: SenderType,
        emotionType: EmotionType?,
        content: String,
        repliesToMessageId: Long?,
        rootMessageId: Long? = null,
    ): Message {
        this.updatedAt = Instant.now()
        return Message(
            conversationId = this.id,
            senderType = senderType,
            emotionType = emotionType,
            content = content,
            repliesToMessageId = repliesToMessageId,
            rootMessageId = rootMessageId,
        )
    }
}
