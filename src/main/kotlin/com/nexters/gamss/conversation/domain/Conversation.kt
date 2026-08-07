package com.nexters.gamss.conversation.domain

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
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
 * 채팅방. 사용자가 감정을 남기고 캐릭터가 반응하는 대화 세션 하나를 나타내며,
 * 말풍선(발화)은 [Message]로 저장된다. 회원은 memberId(ID 참조)로 연결한다.
 */
@Entity
@Table(name = "conversations")
@EntityListeners(AuditingEntityListener::class)
class Conversation(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    initialExcludedEmotionTypes: List<EmotionType> = emptyList(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Embedded
    var title: ConversationTitle? = null
        protected set

    /**
     * 새 채팅방을 만들 때 지정한, 반응하지 않을 캐릭터 목록. 이후로는 바뀌지 않는다(이 채팅방에
     * 이어서 보내는 요청에 다시 실려 와도 무시됨 — [com.nexters.gamss.conversation.service.ConversationService]).
     * [EmotionType.name]을 콤마로 이어붙인 문자열로 저장한다.
     */
    @Column(name = "excluded_emotion_types", length = 255)
    private val excludedEmotionTypesRaw: String? =
        initialExcludedEmotionTypes.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name }

    val excludedEmotionTypes: List<EmotionType>
        get() = excludedEmotionTypesRaw?.split(",")?.map(EmotionType::valueOf) ?: emptyList()

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    var status: ConversationStatus = ConversationStatus.ACTIVE
        protected set

    /**
     * 카드 생성 시점에 저장되는 이 대화 전체의 요약. 다른 대화방 댓글 생성 시 과거 맥락으로 참고한다.
     * 값 자체는 [com.nexters.gamss.conversation.repository.ConversationRepository.updateSummary]로만 갱신한다.
     */
    @Column(name = "summary", columnDefinition = "TEXT")
    var summary: String? = null
        protected set

    /** 카드 생성 LLM 호출 전 CAS 선점 상태([Message.commentStatus]와 같은 패턴). */
    @Enumerated(EnumType.STRING)
    @Column(name = "card_generation_status", length = 20, nullable = false)
    var cardGenerationStatus: CardGenerationStatus = CardGenerationStatus.NONE
        protected set

    @Column(name = "card_generation_status_updated_at")
    var cardGenerationStatusUpdatedAt: Instant? = null
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

    fun isDeleted(): Boolean = status == ConversationStatus.DELETED

    /** 채팅방을 종료한다. 삭제된 방이거나 이미 종료된 방을 다시 종료하면 예외를 던진다. */
    fun end() {
        ensureNotDeleted()
        if (status == ConversationStatus.ENDED) {
            throw BusinessException(ErrorCode.CONVERSATION_ALREADY_ENDED)
        }
        status = ConversationStatus.ENDED
    }

    /** 채팅방을 삭제한다. 이미 삭제된 방을 다시 삭제하면 예외를 던진다. */
    fun delete() {
        ensureNotDeleted()
        status = ConversationStatus.DELETED
    }

    /** 채팅방 제목을 지정·변경한다. 여러 번 호출할 수 있으며, 삭제된 방은 변경할 수 없다. */
    fun rename(title: ConversationTitle) {
        ensureNotDeleted()
        this.title = title
    }

    /** 종료된 채팅방에는 사용자 메시지를 추가할 수 없다. */
    fun ensureActive() {
        ensureNotDeleted()
        if (status == ConversationStatus.ENDED) {
            throw BusinessException(ErrorCode.CONVERSATION_ENDED)
        }
    }

    /** 삭제된 채팅방은 조회·종료·메시지 추가 등 어떤 작업도 할 수 없다. */
    fun ensureNotDeleted() {
        if (status == ConversationStatus.DELETED) {
            throw BusinessException(ErrorCode.CONVERSATION_ALREADY_DELETED)
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
