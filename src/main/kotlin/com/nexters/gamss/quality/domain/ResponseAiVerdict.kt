package com.nexters.gamss.quality.domain

import com.nexters.gamss.conversation.domain.ResponseKind
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.crypto.EncryptedStringConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 캐릭터 응답 하나에 대한 AI 판정. 채점 모델이나 채점 프롬프트 버전이 다르면 따로 쌓인다. */
@Entity
@Table(name = "response_ai_verdict")
class ResponseAiVerdict(
    @Column(name = "message_id", nullable = false, updatable = false)
    val messageId: Long,
    @Column(name = "trigger_message_id", nullable = false, updatable = false)
    val triggerMessageId: Long,
    @Column(name = "conversation_id", nullable = false, updatable = false)
    val conversationId: Long,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "emotion_type", length = 20, nullable = false, updatable = false)
    val emotionType: EmotionType,
    @Enumerated(EnumType.STRING)
    @Column(name = "response_kind", length = 20, nullable = false, updatable = false)
    val responseKind: ResponseKind,
    @Column(name = "message_created_at", nullable = false, updatable = false)
    val messageCreatedAt: Instant,
    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", length = 20, nullable = false, updatable = false)
    val verdict: ResponseVerdict,
    awkwardTypes: Set<AwkwardType>,
    @Convert(converter = EncryptedStringConverter::class)
    @Column(name = "evidence", columnDefinition = "TEXT", updatable = false)
    val evidence: String?,
    @Convert(converter = EncryptedStringConverter::class)
    @Column(name = "reason", columnDefinition = "TEXT", updatable = false)
    val reason: String?,
    @Column(name = "judge_model", length = 100, nullable = false, updatable = false)
    val judgeModel: String,
    @Column(name = "judge_prompt_version", nullable = false, updatable = false)
    val judgePromptVersion: Int,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(name = "awkward_types", length = 200, updatable = false)
    private val awkwardTypesRaw: String? = AwkwardType.toColumn(awkwardTypes)

    val awkwardTypes: Set<AwkwardType>
        get() = AwkwardType.fromColumn(awkwardTypesRaw)
}
