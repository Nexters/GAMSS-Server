package com.nexters.gamss.conversation.domain

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.crypto.BlindIndexer
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
 * 채팅방 안의 말풍선 하나. 사용자 또는 감정 캐릭터의 발화이며,
 * 특정 메시지에 대한 답장이면 repliesToMessageId로 가리킨다.
 *
 * rootMessageId는 이 메시지가 속한 원본 일기(사용자) 메시지를 가리킨다(캐릭터 댓글·티키타카 전용).
 * repliesToMessageId(직접 답장 대상, 체인 한 단계)와 달리 피드 전체를 한 번에 조회하기 위한 용도다.
 */
@Entity
@Table(name = "messages")
@EntityListeners(AuditingEntityListener::class, MessageSearchIndexListener::class)
class Message(
    @Column(name = "conversation_id", nullable = false)
    val conversationId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", length = 20, nullable = false)
    val senderType: SenderType,
    @Enumerated(EnumType.STRING)
    @Column(name = "emotion_type", length = 20)
    val emotionType: EmotionType? = null,
    @Column(name = "content", length = 500, nullable = false)
    val content: String,
    @Column(name = "replies_to_message_id")
    val repliesToMessageId: Long? = null,
    @Column(name = "root_message_id")
    val rootMessageId: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "comment_status", length = 20, nullable = false)
    val commentStatus: CommentStatus = CommentStatus.NONE,
    @Column(name = "comment_status_updated_at")
    val commentStatusUpdatedAt: Instant? = null,
) {
    init {
        require((senderType == SenderType.CHARACTER) == (emotionType != null)) {
            "감정 캐릭터 메시지에만 emotionType을 지정할 수 있습니다."
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    /**
     * 검색용 블라인드 인덱스. [content]가 암호문으로 저장돼 그대로는 검색할 수 없으므로,
     * 평문을 토큰열로 바꿔 여기에 담고 검색은 이 컬럼에 건다
     * ([com.nexters.gamss.global.crypto.BlindIndexer]).
     */
    @Column(name = "content_index", columnDefinition = "TEXT")
    var contentIndex: String? = null
        protected set

    /** 저장 직전에 [MessageSearchIndexListener]가 호출한다. 직접 부를 일은 없다. */
    fun applySearchIndex(indexer: BlindIndexer) {
        contentIndex = indexer.toIndexValue(content)
    }
}
