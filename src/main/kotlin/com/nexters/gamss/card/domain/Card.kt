package com.nexters.gamss.card.domain

import com.nexters.gamss.emotion.domain.EmotionType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 대화 종료 시 만들어지는 감정 카드. 대화 하나를 대표하는 [emotion] 캐릭터와 그 캐릭터의 한 줄
 * 대사([message])로 이루어진다. 회원·대화는 ID 참조([memberId]/[conversationId])로 연결한다.
 *
 * 카드가 속한 캘린더 날짜는 그 대화의 생성시간 기준이라 [conversationCreatedAt]에 비정규화해 둔다
 * — 종료 후 대화 시작 시각은 바뀌지 않으므로 날짜·월별 조회를 단일 테이블로 처리할 수 있다.
 */
@Entity
@Table(name = "cards")
class Card(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Column(name = "conversation_id", nullable = false)
    val conversationId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "emotion", length = 20, nullable = false)
    val emotion: EmotionType,
    @Column(name = "summary", columnDefinition = "TEXT", nullable = false)
    val summary: String,
    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    val message: String,
    @Column(name = "conversation_created_at", nullable = false)
    val conversationCreatedAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    fun isOwnedBy(memberId: Long): Boolean = this.memberId == memberId
}
