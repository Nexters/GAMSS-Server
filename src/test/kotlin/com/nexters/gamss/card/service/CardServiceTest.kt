package com.nexters.gamss.card.service

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.domain.CardSummary
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.error.CardGenerationFailedException
import com.nexters.gamss.llm.generation.CardMessageGenerator
import com.nexters.gamss.llm.generation.CardMessageOutput
import com.nexters.gamss.llm.generation.EmotionExtractionOutput
import com.nexters.gamss.llm.generation.EmotionExtractor
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.service.GenerationLogRecorder
import com.nexters.gamss.tokenlimit.service.DailyTokenLimitService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.QueryTimeoutException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CardServiceTest {
    private val cardRepository = mockk<CardRepository>()
    private val conversationRepository = mockk<ConversationRepository>()
    private val messageRepository = mockk<MessageRepository>()
    private val cardMessageGenerator = mockk<CardMessageGenerator>()
    private val emotionExtractor = mockk<EmotionExtractor>()
    private val generationLogRecorder = mockk<GenerationLogRecorder>(relaxed = true)
    private val dailyTokenLimitService = mockk<DailyTokenLimitService> { every { isWithinLimit(any()) } returns true }
    private val cardPersistenceService = mockk<CardPersistenceService>()
    private val service =
        CardService(
            cardRepository,
            conversationRepository,
            messageRepository,
            cardMessageGenerator,
            emotionExtractor,
            generationLogRecorder,
            dailyTokenLimitService,
            cardPersistenceService,
        )

    private val zone = ZoneId.of("Asia/Seoul")

    private fun endedConversation(memberId: Long = MEMBER_ID): Conversation = Conversation(memberId).apply { end() }

    private fun userMessage(content: String): Message = Message(CONVERSATION_ID, SenderType.USER, content = content)

    /** LLM 호출 전 CAS 선점이 성공하는 경로. */
    private fun stubClaimSuccess() {
        every {
            conversationRepository.updateCardGenerationStatus(
                CONVERSATION_ID,
                CardGenerationStatus.PENDING,
                listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED, CardGenerationStatus.SKIPPED),
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
    fun `카드에는 LLM이 다듬은 한 줄이 저장되고 대화방에는 클라이언트 원본 요약이 남는다`() {
        // 원본은 다른 채팅방 댓글의 '과거 맥락'으로 쓰이므로 카드 문구로 덮어써서는 안 된다.
        val conversation = endedConversation()
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(conversation)
        stubClaimSuccess()
        every { cardMessageGenerator.generate(EmotionType.ANGER, "요약") } returns
            CardMessageOutput("팀장이 자기 할 일을 다 떠넘겼어요", 10, 0)
        val saved = slot<Card>()
        every { cardPersistenceService.save(capture(saved), CONVERSATION_ID, "요약") } answers { firstArg() }

        service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약")

        assertEquals(EmotionType.ANGER, saved.captured.emotion)
        assertEquals("팀장이 자기 할 일을 다 떠넘겼어요", saved.captured.summary)
        // 카드에 남는 것은 한 줄뿐이라 두 필드가 같은 값을 갖는다(어느 필드를 읽는 클라이언트든 깨지지 않게).
        assertEquals(saved.captured.summary, saved.captured.message)
        assertEquals(conversation.createdAt, saved.captured.conversationCreatedAt)
        verify(exactly = 1) { cardPersistenceService.save(any(), CONVERSATION_ID, "요약") }
    }

    @Test
    fun `LLM이 상한을 넘긴 한 줄을 돌려줘도 카드 생성은 실패하지 않고 잘라서 저장한다`() {
        val conversation = endedConversation()
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(conversation)
        stubClaimSuccess()
        every { cardMessageGenerator.generate(EmotionType.ANGER, "요약") } returns
            CardMessageOutput("가".repeat(CardSummary.MAX_LENGTH + 10), 10, 0)
        val saved = slot<Card>()
        every { cardPersistenceService.save(capture(saved), CONVERSATION_ID, "요약") } answers { firstArg() }

        service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약")

        assertTrue(saved.captured.summary.length <= CardSummary.MAX_LENGTH)
        assertEquals(saved.captured.summary, saved.captured.message)
    }

    @Test
    fun `일일 토큰 상한을 넘으면 선점 없이 카드 생성을 막고 DAILY_TOKEN_LIMIT_EXCEEDED`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        every { dailyTokenLimitService.isWithinLimit(MEMBER_ID) } returns false

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.DAILY_TOKEN_LIMIT_EXCEEDED, exception.errorCode)
        verify(exactly = 0) { cardMessageGenerator.generate(any(), any()) }
        verify(
            exactly = 0,
        ) { conversationRepository.updateCardGenerationStatus(CONVERSATION_ID, CardGenerationStatus.PENDING, any(), any()) }
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
    fun `종료 후 삭제된 대화면 CONVERSATION_NOT_ENDED가 아니라 CONVERSATION_ALREADY_DELETED`() {
        val conversation = endedConversation().apply { delete() }
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(conversation)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CONVERSATION_ALREADY_DELETED, exception.errorCode)
    }

    @Test
    fun `선점에 실패했는데 이미 카드가 있으면 LLM 호출 없이 CARD_ALREADY_EXISTS`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        every {
            conversationRepository.updateCardGenerationStatus(
                CONVERSATION_ID,
                CardGenerationStatus.PENDING,
                listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED, CardGenerationStatus.SKIPPED),
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
                listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED, CardGenerationStatus.SKIPPED),
                any(),
            )
        } returns 0
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns false

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_GENERATION_IN_PROGRESS, exception.errorCode)
        verify(exactly = 0) { cardMessageGenerator.generate(any(), any()) }
    }

    @Test
    fun `emotion이 없으면 유저 메시지만 보고 감정을 분류해 카드에 쓴다`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        every {
            messageRepository.findAllByConversationIdAndSenderTypeOrderByIdAsc(CONVERSATION_ID, SenderType.USER)
        } returns listOf(userMessage("오늘 진짜 우울했다"), userMessage("계속 눈물이 났다"))
        every {
            emotionExtractor.extract(listOf("오늘 진짜 우울했다", "계속 눈물이 났다"))
        } returns EmotionExtractionOutput(EmotionType.SADNESS, usedTokens = 5, cachedTokens = 0)
        every { cardMessageGenerator.generate(EmotionType.SADNESS, "요약") } returns CardMessageOutput("오늘은 좀 힘들었지.", 10, 0)
        val saved = slot<Card>()
        every { cardPersistenceService.save(capture(saved), CONVERSATION_ID, "요약") } answers { firstArg() }

        service.createCard(MEMBER_ID, CONVERSATION_ID, null, "요약")

        assertEquals(EmotionType.SADNESS, saved.captured.emotion)
        verify(exactly = 1) {
            generationLogRecorder.record(
                type = GenerationType.CARD_EMOTION,
                success = true,
                attemptCount = 1,
                latencyMs = any(),
                memberId = MEMBER_ID,
                conversationId = CONVERSATION_ID,
                usedTokens = 5,
                cachedTokens = 0,
                inputTokens = any(),
                outputTokens = any(),
            )
        }
    }

    @Test
    fun `emotion이 있으면 감정 분류를 호출하지 않는다`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        every { cardMessageGenerator.generate(EmotionType.ANGER, "요약") } returns CardMessageOutput("대사", 10, 0)
        every { cardPersistenceService.save(any(), CONVERSATION_ID, "요약") } answers { firstArg() }

        service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약")

        verify(exactly = 0) { emotionExtractor.extract(any()) }
    }

    @Test
    fun `유저 메시지가 하나도 없으면 클라이언트 요약으로 감정을 분류한다`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        every {
            messageRepository.findAllByConversationIdAndSenderTypeOrderByIdAsc(CONVERSATION_ID, SenderType.USER)
        } returns emptyList()
        every { emotionExtractor.extract(listOf("요약")) } returns EmotionExtractionOutput(EmotionType.JOY, usedTokens = 5, cachedTokens = 0)
        every { cardMessageGenerator.generate(EmotionType.JOY, "요약") } returns CardMessageOutput("대사", 10, 0)
        every { cardPersistenceService.save(any(), CONVERSATION_ID, "요약") } answers { firstArg() }

        service.createCard(MEMBER_ID, CONVERSATION_ID, null, "요약")

        verify(exactly = 1) { emotionExtractor.extract(listOf("요약")) }
    }

    @Test
    fun `감정 분류에 실패하면 FAILED로 전이하고 CARD_GENERATION_FAILED - 대사 생성은 호출되지 않는다`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        every {
            messageRepository.findAllByConversationIdAndSenderTypeOrderByIdAsc(CONVERSATION_ID, SenderType.USER)
        } returns listOf(userMessage("오늘 일기"))
        val extractionFailure = CardGenerationFailedException("분류 실패")
        every { emotionExtractor.extract(any()) } throws extractionFailure
        stubMarkStatus(CardGenerationStatus.FAILED)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, null, "요약") }

        assertEquals(ErrorCode.CARD_GENERATION_FAILED, exception.errorCode)
        assertEquals(extractionFailure, exception.cause)
        verify(exactly = 0) { cardMessageGenerator.generate(any(), any()) }
        verify(exactly = 0) { cardPersistenceService.save(any(), any(), any()) }
        verify(exactly = 1) {
            conversationRepository.updateCardGenerationStatus(CONVERSATION_ID, CardGenerationStatus.FAILED, any(), any())
        }
        verify(exactly = 1) {
            generationLogRecorder.record(
                type = GenerationType.CARD_EMOTION,
                success = false,
                attemptCount = 1,
                latencyMs = any(),
                memberId = MEMBER_ID,
                conversationId = CONVERSATION_ID,
                usedTokens = any(),
                cachedTokens = any(),
                inputTokens = any(),
                outputTokens = any(),
                failureReason = any(),
            )
        }
    }

    @Test
    fun `분류용 메시지 조회가 실패해도 FAILED로 전이하고 CARD_GENERATION_FAILED - PENDING으로 남지 않는다`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        val readFailure = QueryTimeoutException("조회 타임아웃")
        every {
            messageRepository.findAllByConversationIdAndSenderTypeOrderByIdAsc(CONVERSATION_ID, SenderType.USER)
        } throws readFailure
        stubMarkStatus(CardGenerationStatus.FAILED)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, null, "요약") }

        assertEquals(ErrorCode.CARD_GENERATION_FAILED, exception.errorCode)
        assertEquals(readFailure, exception.cause)
        verify(exactly = 0) { emotionExtractor.extract(any()) }
        verify(exactly = 1) {
            conversationRepository.updateCardGenerationStatus(CONVERSATION_ID, CardGenerationStatus.FAILED, any(), any())
        }
    }

    @Test
    fun `대사 생성에 실패하면 FAILED로 전이하고 CARD_GENERATION_FAILED`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        val generationFailure = CardGenerationFailedException("실패")
        every { cardMessageGenerator.generate(any(), any()) } throws generationFailure
        stubMarkStatus(CardGenerationStatus.FAILED)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_GENERATION_FAILED, exception.errorCode)
        assertEquals(generationFailure, exception.cause)
        verify(exactly = 0) { cardPersistenceService.save(any(), any(), any()) }
        verify(exactly = 1) {
            conversationRepository.updateCardGenerationStatus(CONVERSATION_ID, CardGenerationStatus.FAILED, any(), any())
        }
    }

    /**
     * 재시도 대상이 아닌 예외(SDK 결함 등)로 중단돼도 상태는 되돌아가야 한다. PENDING으로 남으면
     * 사용자의 재시도가 재시도 가능한 503이 아니라 409(생성 중)로 막힌다 — 정리 스케줄러가
     * 타임아웃시킬 때까지다. 실패 로그가 빠지면 실패율·비용 집계도 함께 샌다.
     */
    @Test
    fun `감정 분류가 재시도 대상이 아닌 예외로 끊겨도 FAILED로 전이하고 실패 로그를 남긴다`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        every {
            messageRepository.findAllByConversationIdAndSenderTypeOrderByIdAsc(CONVERSATION_ID, SenderType.USER)
        } returns listOf(userMessage("오늘 일기"))
        val sdkFailure = IllegalStateException("SDK 응답에 후보가 없다")
        every { emotionExtractor.extract(any()) } throws sdkFailure
        stubMarkStatus(CardGenerationStatus.FAILED)

        // 업무 오류로 위장하지 않고 원래 예외를 그대로 올린다.
        val thrown = assertFailsWith<IllegalStateException> { service.createCard(MEMBER_ID, CONVERSATION_ID, null, "요약") }

        assertEquals(sdkFailure, thrown)
        // 재시도 대상이 아니므로 다시 부르지 않는다.
        verify(exactly = 1) { emotionExtractor.extract(any()) }
        verify(exactly = 0) { cardMessageGenerator.generate(any(), any()) }
        verify(exactly = 1) {
            conversationRepository.updateCardGenerationStatus(CONVERSATION_ID, CardGenerationStatus.FAILED, any(), any())
        }
        verify(exactly = 1) {
            generationLogRecorder.record(
                type = GenerationType.CARD_EMOTION,
                success = false,
                attemptCount = 1,
                latencyMs = any(),
                memberId = MEMBER_ID,
                conversationId = CONVERSATION_ID,
                usedTokens = any(),
                cachedTokens = any(),
                inputTokens = any(),
                outputTokens = any(),
                failureReason = "IllegalStateException",
            )
        }
    }

    /** [감정 분류가 재시도 대상이 아닌 예외로 끊겨도 FAILED로 전이하고 실패 로그를 남긴다]와 같은 계약. */
    @Test
    fun `대사 생성이 재시도 대상이 아닌 예외로 끊겨도 FAILED로 전이하고 실패 로그를 남긴다`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        val sdkFailure = IllegalStateException("SDK 응답에 후보가 없다")
        every { cardMessageGenerator.generate(any(), any()) } throws sdkFailure
        stubMarkStatus(CardGenerationStatus.FAILED)

        val thrown =
            assertFailsWith<IllegalStateException> {
                service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약")
            }

        assertEquals(sdkFailure, thrown)
        verify(exactly = 1) { cardMessageGenerator.generate(any(), any()) }
        verify(exactly = 0) { cardPersistenceService.save(any(), any(), any()) }
        verify(exactly = 1) {
            conversationRepository.updateCardGenerationStatus(CONVERSATION_ID, CardGenerationStatus.FAILED, any(), any())
        }
        verify(exactly = 1) {
            generationLogRecorder.record(
                type = GenerationType.CARD,
                success = false,
                attemptCount = 1,
                latencyMs = any(),
                memberId = MEMBER_ID,
                conversationId = CONVERSATION_ID,
                usedTokens = any(),
                cachedTokens = any(),
                inputTokens = any(),
                outputTokens = any(),
                failureReason = "IllegalStateException",
            )
        }
    }

    @Test
    fun `선점 이후에도 저장 시점에 유니크 위반이 나면 DONE으로 맞추고 CARD_ALREADY_EXISTS로 변환된다`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        every { cardMessageGenerator.generate(any(), any()) } returns CardMessageOutput("대사", 10, 0)
        val saveFailure = DataIntegrityViolationException("duplicate")
        every { cardPersistenceService.save(any(), any(), any()) } throws saveFailure
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns true
        stubMarkStatus(CardGenerationStatus.DONE)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_ALREADY_EXISTS, exception.errorCode)
        assertEquals(saveFailure, exception.cause)
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

    /**
     * 저장 실패도 CAS 선점 이후다. PENDING 으로 남기면 사용자의 재시도가 재시도 가능한 503 이 아니라
     * 409(생성 중)로 막힌다 — 정리 스케줄러가 타임아웃시킬 때까지다.
     */
    @Test
    fun `저장이 유니크 위반도 상태 충돌도 아닌 이유로 실패해도 FAILED로 전이한다`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        every { cardMessageGenerator.generate(any(), any()) } returns CardMessageOutput("대사", 10, 0)
        val saveFailure = QueryTimeoutException("저장 타임아웃")
        every { cardPersistenceService.save(any(), any(), any()) } throws saveFailure
        stubMarkStatus(CardGenerationStatus.FAILED)

        // 업무 오류로 위장하지 않고 원래 예외를 그대로 올린다.
        val exception =
            assertFailsWith<QueryTimeoutException> {
                service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약")
            }

        assertEquals(saveFailure, exception)
        verify(exactly = 1) {
            conversationRepository.updateCardGenerationStatus(CONVERSATION_ID, CardGenerationStatus.FAILED, any(), any())
        }
    }

    @Test
    fun `저장 시점 상태 전이가 채팅방 삭제로 실패하면 CONVERSATION_ALREADY_DELETED로 변환된다`() {
        val activeConversation = endedConversation()
        val deletedConversation = endedConversation().apply { delete() }
        every { conversationRepository.findById(CONVERSATION_ID) } returnsMany
            listOf(Optional.of(activeConversation), Optional.of(deletedConversation))
        stubClaimSuccess()
        every { cardMessageGenerator.generate(any(), any()) } returns CardMessageOutput("대사", 10, 0)
        every {
            cardPersistenceService.save(any(), any(), any())
        } throws CardGenerationStateConflictException("conflict")

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CONVERSATION_ALREADY_DELETED, exception.errorCode)
        // 이미 PENDING이 아니라는 뜻(삭제됨)이라 되돌릴 대상 자체가 없다 — 되돌리기를 시도하지 않는다.
        verify(exactly = 0) {
            conversationRepository.updateCardGenerationStatus(CONVERSATION_ID, CardGenerationStatus.FAILED, any(), any())
        }
    }

    @Test
    fun `저장 시점 상태 전이가 삭제 아닌 이유로 실패하면(PENDING 타임아웃 리셋 등) CARD_GENERATION_FAILED로 변환된다`() {
        every { conversationRepository.findById(CONVERSATION_ID) } returns Optional.of(endedConversation())
        stubClaimSuccess()
        every { cardMessageGenerator.generate(any(), any()) } returns CardMessageOutput("대사", 10, 0)
        every {
            cardPersistenceService.save(any(), any(), any())
        } throws CardGenerationStateConflictException("conflict")

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_GENERATION_FAILED, exception.errorCode)
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
