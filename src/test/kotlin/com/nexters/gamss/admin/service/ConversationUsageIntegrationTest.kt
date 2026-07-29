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
import org.springframework.data.domain.PageRequest
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 대화방별 사용량 집계(메시지 수·카드 여부·토큰 합)를 실제 MySQL 로 검증한다.
 */
class ConversationUsageIntegrationTest : RepositoryTest() {
    @Autowired private lateinit var conversationUsageService: ConversationUsageService

    @Autowired private lateinit var memberRepository: MemberRepository

    @Autowired private lateinit var conversationRepository: ConversationRepository

    @Autowired private lateinit var messageRepository: MessageRepository

    @Autowired private lateinit var cardRepository: CardRepository

    @Autowired private lateinit var generationLogRepository: GenerationLogRepository

    @Test
    fun `대화방별 메시지 수·카드 여부·토큰 합·비용을 집계한다`() {
        val member = memberRepository.save(Member())
        val withCard = conversationRepository.save(Conversation(memberId = member.id))
        val withoutCard = conversationRepository.save(Conversation(memberId = member.id))

        // withCard: 유저 2, 캐릭터 3, 카드 O. 토큰·비용은 아래 두 로그 합.
        messageRepository.save(Message(conversationId = withCard.id, senderType = SenderType.USER, content = "일기"))
        messageRepository.save(Message(conversationId = withCard.id, senderType = SenderType.USER, content = "답장"))
        repeat(3) {
            messageRepository.save(
                Message(
                    conversationId = withCard.id,
                    senderType = SenderType.CHARACTER,
                    emotionType = EmotionType.ANGER,
                    content = "댓글-$it",
                ),
            )
        }
        cardRepository.save(
            Card(
                memberId = member.id,
                conversationId = withCard.id,
                emotion = EmotionType.ANGER,
                summary = "요약",
                message = "한 줄",
                conversationCreatedAt = Instant.now(),
            ),
        )
        // 총 토큰 7000 / 캐시 3000 / 비용 = row1(2000×0.25 + 3000×0.025 + 1000×1.50)/1e6 + row2(1000×0.25)/1e6
        //        = 0.002075 + 0.00025 = 0.002325 → 4자리 반올림 0.0023
        saveLog(conversationId = withCard.id, input = 5000, cached = 3000, output = 1000)
        saveLog(conversationId = withCard.id, input = 1000, cached = 0, output = 0)

        // withoutCard: 유저 1, 캐릭터 0, 카드 X, 토큰 없음
        messageRepository.save(Message(conversationId = withoutCard.id, senderType = SenderType.USER, content = "일기만"))

        val page = conversationUsageService.getUsage(PageRequest.of(0, 20))
        val byId = page.content.associateBy { it.conversationId }

        assertEquals(2, page.totalElements)

        val a = byId.getValue(withCard.id)
        assertEquals(2, a.userMessageCount)
        assertEquals(3, a.characterMessageCount)
        assertTrue(a.cardCreated)
        assertEquals(7000, a.totalTokens)
        assertEquals(3000, a.cachedTokens)
        assertEquals(0.0023, a.estimatedCostUsd, 1e-9)

        val b = byId.getValue(withoutCard.id)
        assertEquals(1, b.userMessageCount)
        assertEquals(0, b.characterMessageCount)
        assertFalse(b.cardCreated)
        assertEquals(0, b.totalTokens)
        assertEquals(0, b.cachedTokens)
        assertEquals(0.0, b.estimatedCostUsd, 1e-9)
    }

    private fun saveLog(
        conversationId: Long,
        input: Int,
        cached: Int,
        output: Int,
    ) {
        generationLogRepository.save(
            GenerationLog(
                generationType = GenerationType.COMMENT,
                model = "gemini-3.1-flash-lite",
                conversationId = conversationId,
                success = true,
                attemptCount = 1,
                // usedTokens(총 토큰) = 입력 + 출력. cached 는 input 의 부분집합.
                usedTokens = input + output,
                cachedTokens = cached,
                inputTokens = input,
                outputTokens = output,
                latencyMs = 100,
                createdAt = Instant.now(),
            ),
        )
    }
}
