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

/** 관리자가 직접 내린 판정. 관리자마다 응답 하나에 하나이고, 다시 판정하면 덮어쓴다. */
@Entity
@Table(name = "response_human_verdict")
class ResponseHumanVerdict(
    @Column(name = "message_id", nullable = false, updatable = false)
    val messageId: Long,
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
    @Column(name = "admin_email", nullable = false, updatable = false)
    val adminEmail: String,
    verdict: ResponseVerdict,
    awkwardTypes: Set<AwkwardType>,
    memo: String?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", length = 20, nullable = false)
    var verdict: ResponseVerdict = verdict
        protected set

    @Column(name = "awkward_types", length = 200)
    private var awkwardTypesRaw: String? = typesColumn(verdict, awkwardTypes)

    @Convert(converter = EncryptedStringConverter::class)
    @Column(name = "memo", columnDefinition = "TEXT")
    var memo: String? = memo
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    val awkwardTypes: Set<AwkwardType>
        get() = AwkwardType.fromColumn(awkwardTypesRaw)

    fun change(
        verdict: ResponseVerdict,
        awkwardTypes: Set<AwkwardType>,
        memo: String?,
    ) {
        this.verdict = verdict
        this.awkwardTypesRaw = typesColumn(verdict, awkwardTypes)
        this.memo = memo
        this.updatedAt = Instant.now()
    }

    private fun typesColumn(
        verdict: ResponseVerdict,
        awkwardTypes: Set<AwkwardType>,
    ): String? = if (verdict == ResponseVerdict.AWKWARD) AwkwardType.toColumn(awkwardTypes) else null
}
