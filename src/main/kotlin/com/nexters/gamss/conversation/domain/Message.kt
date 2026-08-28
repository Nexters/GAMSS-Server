package com.nexters.gamss.conversation.domain

import com.nexters.gamss.conversation.search.MessageSearchIndexListener
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.crypto.EncryptedStringConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
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
    @Convert(converter = EncryptedStringConverter::class)
    @Column(name = "content", length = ENCRYPTED_CONTENT_LENGTH, nullable = false)
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
        // 컬럼이 암호문 크기에 맞춰 넓어지면서 평문 상한이 사라졌다. 원래 VARCHAR(500)이 하던
        // 안전망을 여기서 대신 지킨다 — 없으면 LLM 이 비정상적으로 긴 댓글을 뱉었을 때 그대로 들어간다.
        // 코드 포인트로 센다. length 는 UTF-16 코드 유닛이라 이모지 하나를 두 글자로 세어,
        // 이모지가 섞인 일기에서 상한이 사람이 보는 글자 수보다 일찍 걸린다.
        require(content.codePointCount(0, content.length) <= MAX_CONTENT_LENGTH) {
            "메시지는 ${MAX_CONTENT_LENGTH}자 이하여야 합니다."
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
     * 검색용 인덱스 값. [content]가 암호문으로 저장돼 그대로는 검색할 수 없으므로, 검색은 이 컬럼에 건다.
     *
     * 이 값을 **어떻게 만드는지는 엔티티가 알지 않는다.** 만드는 쪽은
     * [com.nexters.gamss.conversation.search.MessageSearchIndexListener] 이고, 여기는 완성된 값을 보관만
     * 한다. 검색 방식이 바뀌어도 이 엔티티는 바뀌지 않아야 한다.
     */
    @Column(name = "content_index", columnDefinition = "TEXT")
    var contentIndex: String? = null
        protected set

    /**
     * 저장 직전에 [MessageSearchIndexListener] 가 부른다. **무엇을 인덱싱할지는 여기서 정하고,
     * 어떻게 만드는지는 [toIndex] 가 안다.**
     *
     * 완성된 값을 받지 않고 함수를 받는 이유는, 값을 받으면 부르는 쪽이 [content] 를 인덱싱한다는
     * 사실까지 알아야 하기 때문이다. 그러면 인덱싱 대상이 바뀔 때 엔티티와 리스너를 같이 고쳐야
     * 하고 한쪽만 고치면 조용히 어긋난다.
     */
    internal fun applySearchIndex(toIndex: (String) -> String?) {
        contentIndex = toIndex(content)
    }

    companion object {
        /** 평문 상한. 사용자 메시지는 요청 단계에서 더 짧게(140자) 걸리고, 캐릭터 댓글이 이 값을 쓴다. */
        const val MAX_CONTENT_LENGTH = 500

        /**
         * 암호문을 담기 위한 컬럼 폭. 한글 [MAX_CONTENT_LENGTH]자는 UTF-8 1,500바이트이고 IV·인증 태그가
         * 붙어 Base64 로 감싸면 약 2,050자가 된다. 상한을 코드 포인트로 세므로 최악은 이모지 500개
         * (2,000바이트)이고 그때가 약 2,710자다. 여유를 둔 값이라 평문 상한과 혼동하지 말 것.
         */
        const val ENCRYPTED_CONTENT_LENGTH = 3000
    }
}
