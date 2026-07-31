package com.nexters.gamss.card.service

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.error.CardGenerationFailedException
import com.nexters.gamss.llm.generation.CardMessageGenerator
import com.nexters.gamss.llm.generation.CardMessageOutput
import com.nexters.gamss.monitoring.service.GenerationLogRecorder
import com.nexters.gamss.tokenlimit.service.DailyTokenLimitService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CardServiceTest {
    private val cardRepository = mockk<CardRepository>()
    private val conversationRepository = mockk<ConversationRepository>()
    private val cardMessageGenerator = mockk<CardMessageGenerator>()
    private val generationLogRecorder = mockk<GenerationLogRecorder>(relaxed = true)
    private val dailyTokenLimitService = mockk<DailyTokenLimitService> { every { isWithinLimit(any()) } returns true }
    private val cardPersistenceService = mockk<CardPersistenceService>()
    private val service =
        CardService(
            cardRepository,
            conversationRepository,
            cardMessageGenerator,
            generationLogRecorder,
            dailyTokenLimitService,
            cardPersistenceService,
        )

    private val zone = ZoneId.of("Asia/Seoul")

    private fun endedConversation(memberId: Long = MEMBER_ID): Conversation = Conversation(memberId).apply { end() }

    /** LLM 호출 전 CAS 선점이 성공하는 경로. */
    private fun stubClaimSuccess() {
        every {
            conversationRepository.updateCardGenerationStatus(
                CONVERSATION_ID,
                CardGenerationStatus.PENDING,
                listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED),
                any(),
            )
        } returns 1
    }

    private fun stubMarkStatus(
        to: CardGenerationStatus,
        returns: Int = 1,
    ) {
        every {
            conversationRepository.updateCardGenerationStatus(CONVERSATION_ID, to, listOf(CardGenerationStatus.PENDING), any())
        } returns returns
    }

    @Test
    fun `종료된 대화에 카드를 생성하고 대화 요약을 저장한다`() {
        val conversation = endedConversation()
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(conversation)
        stubClaimSuccess()
        every { cardMessageGenerator.generate(EmotionType.ANGER, "요약") } returns CardMessageOutput("얘 오늘 건들면 안 됨.", 10, 0)
        val saved = slot<Card>()
        every { cardPersistenceService.save(capture(saved), CONVERSATION_ID, "요약") } answers { firstArg() }

        service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약")

        assertEquals(EmotionType.ANGER, saved.captured.emotion)
        assertEquals("요약", saved.captured.summary)
        assertEquals("얘 오늘 건들면 안 됨.", saved.captured.message)
        assertEquals(conversation.createdAt, saved.captured.conversationCreatedAt)
        verify(exactly = 1) { cardPersistenceService.save(any(), CONVERSATION_ID, "요약") }
    }

    @Test
    fun `일일 토큰 상한을 넘으면 카드 생성을 막고 DAILY_TOKEN_LIMIT_EXCEEDED`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        every { dailyTokenLimitService.isWithinLimit(MEMBER_ID) } returns false
        stubMarkStatus(CardGenerationStatus.FAILED)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.DAILY_TOKEN_LIMIT_EXCEEDED, exception.errorCode)
        verify(exactly = 0) { cardMessageGenerator.generate(any(), any()) }
    }

    @Test
    fun `존재하지 않는 대화면 CONVERSATION_NOT_FOUND`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.empty()

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `본인 대화가 아니면 CONVERSATION_ACCESS_DENIED`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation(memberId = OTHER_MEMBER_ID))

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CONVERSATION_ACCESS_DENIED, exception.errorCode)
    }

    @Test
    fun `종료되지 않은 대화면 CONVERSATION_NOT_ENDED`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(Conversation(MEMBER_ID))

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CONVERSATION_NOT_ENDED, exception.errorCode)
    }

    @Test
    fun `선점에 실패했는데 이미 카드가 있으면 LLM 호출 없이 CARD_ALREADY_EXISTS`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        every {
            conversationRepository.updateCardGenerationStatus(
                CONVERSATION_ID,
                CardGenerationStatus.PENDING,
                listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED),
                any(),
            )
        } returns 0
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns true

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_ALREADY_EXISTS, exception.errorCode)
        verify(exactly = 0) { cardMessageGenerator.generate(any(), any()) }
    }

    @Test
    fun `선점에 실패했는데 카드가 아직 없으면(동시 생성 중) LLM 호출 없이 CARD_GENERATION_IN_PROGRESS`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        every {
            conversationRepository.updateCardGenerationStatus(
                CONVERSATION_ID,
                CardGenerationStatus.PENDING,
                listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED),
                any(),
            )
        } returns 0
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns false

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_GENERATION_IN_PROGRESS, exception.errorCode)
        verify(exactly = 0) { cardMessageGenerator.generate(any(), any()) }
    }

    @Test
    fun `대사 생성에 실패하면 FAILED로 전이하고 CARD_GENERATION_FAILED`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        every { cardMessageGenerator.generate(any(), any()) } throws CardGenerationFailedException("실패")
        stubMarkStatus(CardGenerationStatus.FAILED)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_GENERATION_FAILED, exception.errorCode)
        verify(exactly = 0) { cardPersistenceService.save(any(), any(), any()) }
        verify(exactly = 1) {
            conversationRepository.updateCardGenerationStatus(CONVERSATION_ID, CardGenerationStatus.FAILED, any(), any())
        }
    }

    @Test
    fun `선점 이후에도 저장 시점에 유니크 위반이 나면 DONE으로 맞추고 CARD_ALREADY_EXISTS로 변환된다`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        every { cardMessageGenerator.generate(any(), any()) } returns CardMessageOutput("대사", 10, 0)
        every { cardPersistenceService.save(any(), any(), any()) } throws DataIntegrityViolationException("duplicate")
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns true
        stubMarkStatus(CardGenerationStatus.DONE)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_ALREADY_EXISTS, exception.errorCode)
        verify(exactly = 1) {
            conversationRepository.updateCardGenerationStatus(CONVERSATION_ID, CardGenerationStatus.DONE, any(), any())
        }
    }

    @Test
    fun `저장 시점 유니크 위반인데 실제로는 카드가 없으면 FAILED로 전이하고 원래 예외를 그대로 던진다`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        every { cardMessageGenerator.generate(any(), any()) } returns CardMessageOutput("대사", 10, 0)
        val saveFailure = DataIntegrityViolationException("not-null constraint")
        every { cardPersistenceService.save(any(), any(), any()) } throws saveFailure
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns false
        stubMarkStatus(CardGenerationStatus.FAILED)

        val exception =
            assertFailsWith<DataIntegrityViolationException> {
                service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약")
            }

        assertEquals(saveFailure, exception)
        verify(exactly = 1) {
            conversationRepository.updateCardGenerationStatus(CONVERSATION_ID, CardGenerationStatus.FAILED, any(), any())
        }
    }

    @Test
    fun `날짜별 조회는 그날 자정부터 다음날 자정까지 KST 범위로 조회한다`() {
        val start = slot<Instant>()
        val end = slot<Instant>()
        every {
            cardRepository.findAllByMemberIdAndConversationCreatedAtInRange(MEMBER_ID, capture(start), capture(end))
        } returns emptyList()

        service.getCardsByDate(MEMBER_ID, LocalDate.of(2026, 7, 23))

        assertEquals(LocalDate.of(2026, 7, 23).atStartOfDay(zone).toInstant(), start.captured)
        assertEquals(LocalDate.of(2026, 7, 24).atStartOfDay(zone).toInstant(), end.captured)
    }

    @Test
    fun `월별 조회는 그달 1일부터 다음달 1일까지 KST 범위로 조회한다`() {
        val start = slot<Instant>()
        val end = slot<Instant>()
        every {
            cardRepository.findAllByMemberIdAndConversationCreatedAtInRange(MEMBER_ID, capture(start), capture(end))
        } returns emptyList()

        service.getCardsByMonth(MEMBER_ID, YearMonth.of(2026, 7))

        assertEquals(LocalDate.of(2026, 7, 1).atStartOfDay(zone).toInstant(), start.captured)
        assertEquals(LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant(), end.captured)
    }

    private companion object {
        const val MEMBER_ID = 1L
        const val OTHER_MEMBER_ID = 2L
        const val CONVERSATION_ID = 10L
    }
}
