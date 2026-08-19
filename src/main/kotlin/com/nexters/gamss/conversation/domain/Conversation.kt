package com.nexters.gamss.conversation.domain

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.crypto.BlindIndexer
import com.nexters.gamss.global.crypto.EncryptedStringConverter
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Convert
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
@EntityListeners(AuditingEntityListener::class, ConversationSearchIndexListener::class)
class Conversation(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    initialExcludedEmotionTypes: ExcludedEmotionTypes = ExcludedEmotionTypes.EMPTY,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Embedded
    var title: ConversationTitle? = null
        protected set

    /**
     * 제목의 검색용 블라인드 인덱스. 제목도 암호문으로 저장되므로 검색은 이 컬럼에 건다
     * ([com.nexters.gamss.global.crypto.BlindIndexer]). 제목이 없으면 null 이다.
     */
    @Column(name = "title_index", columnDefinition = "TEXT")
    var titleIndex: String? = null
        protected set

    /**
     * 새 채팅방을 만들 때 지정한, 반응하지 않을 캐릭터 목록. 이후로는 바뀌지 않는다(이 채팅방에
     * 이어서 보내는 요청에 다시 실려 와도 무시됨 — [com.nexters.gamss.conversation.service.ConversationService]).
     * 중복 제거·개수 검증·직렬화 규칙은 [ExcludedEmotionTypes]가 전담한다.
     */
    @Column(name = "excluded_emotion_types", length = 255)
    private val excludedEmotionTypesRaw: String? = initialExcludedEmotionTypes.toColumnValue()

    val excludedEmotionTypes: ExcludedEmotionTypes
        get() = ExcludedEmotionTypes.fromColumnValue(excludedEmotionTypesRaw)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    var status: ConversationStatus = ConversationStatus.ACTIVE
        protected set

    /**
     * 이 대화 전체를 프론트가 압축한 요약. 진행 중에는 메시지를 보낼 때마다 최신 임시 요약으로
     * 갱신되고([updateSummary]), 카드 생성 시점에 그때의 확정 요약으로 덮인다
     * ([com.nexters.gamss.conversation.repository.ConversationRepository.updateSummary] — 엔티티를
     * 로드하지 않는 경로라 벌크 쿼리를 쓴다). 종료된 방의 요약은 다른 대화방 댓글 생성 시 과거
     * 맥락으로 참고하고, 자동 종료 배치는 카드 요약으로 쓴다.
     *
     * DB에는 암호문으로 저장된다([com.nexters.gamss.global.crypto.EncryptedStringConverter]) —
     * 대화를 통째로 압축한 값이라 이것만 새도 그날 무슨 이야기를 했는지가 드러난다.
     */
    @Convert(converter = EncryptedStringConverter::class)
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

    /**
     * 진행 중 요약을 최신값으로 갱신한다. 메시지를 보낼 때마다 호출되므로 마지막 값이 곧 그 대화의
     * 최신 요약이고, 사용자가 종료 버튼을 누르지 않아도 자동 종료 배치가 이 값으로 카드를 만든다.
     */
    fun updateSummary(summary: String) {
        ensureNotDeleted()
        this.summary = summary
    }

    /** 채팅방 제목을 지정·변경한다. 여러 번 호출할 수 있으며, 삭제된 방은 변경할 수 없다. */
    fun rename(title: ConversationTitle) {
        ensureNotDeleted()
        this.title = title
    }

    /** 저장 직전에 [ConversationSearchIndexListener]가 호출한다. 직접 부를 일은 없다. */
    fun applySearchIndex(indexer: BlindIndexer) {
        titleIndex = title?.value?.let(indexer::toIndexValue)
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
