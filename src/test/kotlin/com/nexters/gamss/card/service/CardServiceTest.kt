package com.nexters.gamss.card.service

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.generation.CardGenerationFailedException
import com.nexters.gamss.llm.generation.CardMessageGenerator
import com.nexters.gamss.llm.generation.CardMessageOutput
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
    private val service = CardService(cardRepository, conversationRepository, cardMessageGenerator)

    private val zone = ZoneId.of("Asia/Seoul")

    private fun endedConversation(memberId: Long = MEMBER_ID): Conversation = Conversation(memberId).apply { end() }

    @Test
    fun `종료된 대화에 카드를 생성한다`() {
        val conversation = endedConversation()
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(conversation)
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns false
        every { cardMessageGenerator.generate(EmotionType.ANGER, "요약") } returns CardMessageOutput("얘 오늘 건들면 안 됨.", 10)
        val saved = slot<Card>()
        every { cardRepository.saveAndFlush(capture(saved)) } answers { firstArg() }

        service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약")

        assertEquals(EmotionType.ANGER, saved.captured.emotion)
        assertEquals("요약", saved.captured.summary)
        assertEquals("얘 오늘 건들면 안 됨.", saved.captured.message)
        assertEquals(conversation.createdAt, saved.captured.conversationCreatedAt)
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
    fun `이미 카드가 있으면 CARD_ALREADY_EXISTS`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns true

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_ALREADY_EXISTS, exception.errorCode)
    }

    @Test
    fun `대사 생성에 실패하면 CARD_GENERATION_FAILED`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns false
        every { cardMessageGenerator.generate(any(), any()) } throws CardGenerationFailedException("실패")

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_GENERATION_FAILED, exception.errorCode)
        verify(exactly = 0) { cardRepository.saveAndFlush(any()) }
    }

    @Test
    fun `동시 요청이 사전 검사를 함께 통과해도 유니크 위반은 CARD_ALREADY_EXISTS로 변환된다`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns false
        every { cardMessageGenerator.generate(any(), any()) } returns CardMessageOutput("대사", 10)
        every { cardRepository.saveAndFlush(any()) } throws DataIntegrityViolationException("duplicate")

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_ALREADY_EXISTS, exception.errorCode)
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
