package com.nexters.gamss.admin.service

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.monitoring.domain.GenerationLog
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.repository.GenerationLogRepository
import com.nexters.gamss.support.RepositoryTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 대시보드 집계 쿼리(조인·group by·프로젝션·앱단 백분위)를 실제 MySQL 로 검증한다.
 */
class DashboardStatsIntegrationTest : RepositoryTest() {
    @Autowired private lateinit var usageStatsService: UsageStatsService

    @Autowired private lateinit var qualityStatsService: QualityStatsService

    @Autowired private lateinit var memberRepository: MemberRepository

    @Autowired private lateinit var conversationRepository: ConversationRepository

    @Autowired private lateinit var messageRepository: MessageRepository

    @Autowired private lateinit var cardRepository: CardRepository

    @Autowired private lateinit var generationLogRepository: GenerationLogRepository

    @Test
    fun `사용량 지표를 집계한다`() {
        val member = memberRepository.save(Member())
        val conversation = conversationRepository.save(Conversation(memberId = member.id))
        messageRepository.save(Message(conversationId = conversation.id, senderType = SenderType.USER, content = "오늘 일기"))
        repeat(2) {
            messageRepository.save(
                Message(
                    conversationId = conversation.id,
                    senderType = SenderType.CHARACTER,
                    emotionType = EmotionType.ANGER,
                    content = "댓글-$it",
                ),
            )
        }
        cardRepository.save(
            Card(
                memberId = member.id,
                conversationId = conversation.id,
                emotion = EmotionType.ANGER,
                summary = "요약",
                message = "한 줄 대사",
                conversationCreatedAt = Instant.now(),
            ),
        )

        val usage = usageStatsService.getUsageStats(14)

        assertEquals(1, usage.todayConversations)
        assertEquals(1, usage.todayUserMessages)
        // 유저 1명이 유저 메시지 1개 → 유저당 평균 1.0
        assertEquals(1.0, usage.avgMessagesPerUser)
        assertEquals(1, usage.todayCards)
        assertEquals(1, usage.todaySignups)
        assertEquals(1, usage.dau)
        assertEquals(1, usage.emotionDistribution.first { it.emotion == "ANGER" }.count)
        val todayActivity = usage.dailyActivity.last()
        assertEquals(1, todayActivity.conversations)
        assertEquals(3, todayActivity.messages)
        assertEquals(1, todayActivity.cards)
    }

    @Test
    fun `LLM 품질 지표를 집계한다`() {
        val now = Instant.now()
        generationLogRepository.save(
            GenerationLog(
                generationType = GenerationType.COMMENT,
                model = "gemini-3.1-flash-lite",
                success = true,
                attemptCount = 1,
                usedTokens = 5500,
                cachedTokens = 3000,
                inputTokens = 5000,
                outputTokens = 500,
                latencyMs = 100,
                createdAt = now,
            ),
        )
        generationLogRepository.save(
            GenerationLog(
                generationType = GenerationType.REPLY,
                model = "gemini-test",
                success = false,
                attemptCount = 2,
                latencyMs = 200,
                failureReason = "TimeoutException",
                createdAt = now,
            ),
        )

        val quality = qualityStatsService.getQualityStats(14)

        assertEquals(2, quality.totalGenerations)
        assertEquals(1, quality.successGenerations)
        assertEquals(1, quality.failedGenerations)
        assertEquals(50.0, quality.successRate)
        assertEquals(3, quality.totalLlmCalls)
        assertEquals(50.0, quality.retryRate)
        assertEquals(150, quality.avgLatencyMs)
        assertEquals(200, quality.p95LatencyMs)
        assertEquals(5500, quality.totalTokens)
        assertEquals(3000, quality.cachedTokens)
        // 적중률 = 캐시(3000) / 입력(5000) = 60.0
        assertEquals(60.0, quality.cacheHitRate)
        // 비용 = 비캐시입력(2000)×0.25 + 캐시(3000)×0.025 + 출력(500)×1.5 (per 1M) = 0.001325 → 0.0013
        assertEquals(0.0013, quality.estimatedCostUsd)
        assertEquals(0, quality.stuckPending)
    }

    @Test
    fun `데이터가 없으면 평균·비율은 null이다`() {
        val usage = usageStatsService.getUsageStats(14)
        assertNull(usage.avgMessagesPerUser, "활동 유저가 없으면 유저당 평균은 null")

        val quality = qualityStatsService.getQualityStats(14)
        assertNull(quality.successRate, "생성이 없으면 성공률은 null")
        assertNull(quality.retryRate, "생성이 없으면 재시도율은 null")
    }
}
