package com.nexters.gamss.conversation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 생성 1회의 기록. 생성 1회는 트리거 유저 메시지(일기 또는 답장) 하나에 대응한다(성공은 CAS 로 한 번뿐). */
@Entity
@Table(name = "response_generation")
class ResponseGeneration(
    @Column(name = "trigger_message_id", nullable = false, updatable = false)
    val triggerMessageId: Long,
    @Column(name = "conversation_id", nullable = false, updatable = false)
    val conversationId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20, nullable = false, updatable = false)
    val kind: ResponseKind,
    @Column(name = "model", length = 100, updatable = false)
    val model: String?,
    @Column(name = "common_prompt_version", updatable = false)
    val commonPromptVersion: Int?,
    @Column(name = "type_prompt_version", updatable = false)
    val typePromptVersion: Int?,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
