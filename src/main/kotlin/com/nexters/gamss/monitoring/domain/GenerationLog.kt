package com.nexters.gamss.monitoring.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import jakarta.persistence.GenerationType as JpaGenerationType

/**
 * LLM 생성 1요청의 관측 로그. 대시보드 품질 지표(호출 수·성공률·재시도율·지연·토큰)를 정확히 집계하기
 * 위한 것으로, 도메인 로직과 무관한 순수 기록이다. 기록 실패가 생성 흐름을 깨지 않도록 best-effort로 남긴다.
 */
@Entity
@Table(name = "generation_log")
class GenerationLog(
    @Enumerated(EnumType.STRING)
    @Column(name = "generation_type", length = 20, nullable = false)
    val generationType: GenerationType,
    @Column(name = "model", length = 100, nullable = false)
    val model: String,
    // 유저별 일일 토큰 상한(member_id)·대화방별 사용량(conversation_id) 집계용. 과거 행엔 없으므로 nullable.
    @Column(name = "member_id")
    val memberId: Long? = null,
    @Column(name = "conversation_id")
    val conversationId: Long? = null,
    @Column(name = "success", nullable = false)
    val success: Boolean,
    @Column(name = "attempt_count", nullable = false)
    val attemptCount: Int,
    @Column(name = "used_tokens")
    val usedTokens: Int? = null,
    @Column(name = "cached_tokens")
    val cachedTokens: Int? = null,
    @Column(name = "input_tokens")
    val inputTokens: Int? = null,
    @Column(name = "output_tokens")
    val outputTokens: Int? = null,
    @Column(name = "latency_ms", nullable = false)
    val latencyMs: Long,
    @Column(name = "failure_reason", length = 255)
    val failureReason: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = JpaGenerationType.IDENTITY)
    val id: Long = 0L
}
