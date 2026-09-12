package com.nexters.gamss.card.service

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.domain.CardSummary
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.service.ConversationCardGenerationService
import com.nexters.gamss.conversation.service.ConversationService
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
import com.nexters.gamss.tokenlimit.service.TokenQuotaRecorder
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CardServiceTest {
    private val cardRepository = mockk<CardRepository>()
    private val conversationService = mockk<ConversationService>()
    private val conversationCardGenerationService = mockk<ConversationCardGenerationService>()
    private val cardMessageGenerator = mockk<CardMessageGenerator>()
    private val emotionExtractor = mockk<EmotionExtractor>()
    private val generationLogRecorder = mockk<GenerationLogRecorder>(relaxed = true)
    private val cardPersistenceService = mockk<CardPersistenceService>()
    private val tokenQuotaRecorder = mockk<TokenQuotaRecorder>(relaxed = true)
    private val service =
        CardService(
            cardRepository,
            conversationCardGenerationService,
            conversationService,
            cardMessageGenerator,
            emotionExtractor,
            generationLogRecorder,
            tokenQuotaRecorder,
            cardPersistenceService,
        )

    private val zone = ZoneId.of("Asia/Seoul")

    private fun endedConversation(memberId: Long = MEMBER_ID): Conversation = Conversation(memberId).apply { end() }

    /** 소유·종료 검사를 통과해 이 대화방이 돌아오는 경로. */
    private fun stubOwnedConversation(conversation: Conversation) {
        every { conversationCardGenerationService.getOwnedConversation(CONVERSATION_ID, MEMBER_ID) } returns conversation
    }

    /** LLM 호출 전 CAS 선점이 성공하는 경로. */
    private fun stubClaimSuccess() {
        every { conversationCardGenerationService.claimForCardGeneration(CONVERSATION_ID) } returns true
    }

    private fun stubFinishStatus(
        to: CardGenerationStatus,
        returns: Boolean = true,
    ) {
        every { conversationCardGenerationService.finishCardGeneration(CONVERSATION_ID, to) } returns returns
    }

    @Test
    fun `카드에는 LLM이 다듬은 한 줄이 저장되고 대화방에는 클라이언트 원본 요약이 남는다`() {
        // 원본은 다른 채팅방 댓글의 '과거 맥락'으로 쓰이므로 카드 문구로 덮어써서는 안 된다.
        val conversation = endedConversation()
        stubOwnedConversation(conversation)
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
        stubOwnedConversation(conversation)
        stubClaimSuccess()
        every { cardMessageGenerator.generate(EmotionType.ANGER, "요약") } returns
            CardMessageOutput("가".repeat(CardSummary.MAX_LENGTH + 10), 10, 0)
        val saved = slot<Card>()
        every { cardPersistenceService.save(capture(saved), CONVERSATION_ID, "요약") } answers { firstArg() }

        service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약")

        assertTrue(saved.captured.summary.length <= CardSummary.MAX_LENGTH)
        assertEquals(saved.captured.summary, saved.captured.message)
    }

    /*
     * "일일 토큰을 다 쓴 회원도 카드를 만들 수 있다" 는 여기 없다.
     * CardService 가 DailyTokenLimitService 를 받지 않아 한도 소진 상태를 주입할 곳이 없어,
     * 이름만 그럴싸하고 아무것도 세팅하지 않는 테스트가 된다.
     * 실제 검증은 generation_log 를 상한까지 채워서 하는 쪽에 있다 —
     * com.nexters.gamss.card.controller.CardTokenLimitIntegrationTest
     */

    @Test
    fun `존재하지 않는 대화면 CONVERSATION_NOT_FOUND`() {
        every {
            conversationCardGenerationService.getOwnedConversation(CONVERSATION_ID, MEMBER_ID)
        } throws BusinessException(ErrorCode.CONVERSATION_NOT_FOUND)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `본인 대화가 아니면 CONVERSATION_ACCESS_DENIED`() {
        every {
            conversationCardGenerationService.getOwnedConversation(CONVERSATION_ID, MEMBER_ID)
        } throws BusinessException(ErrorCode.CONVERSATION_ACCESS_DENIED)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CONVERSATION_ACCESS_DENIED, exception.errorCode)
    }

    @Test
    fun `종료되지 않은 대화면 CONVERSATION_NOT_ENDED`() {
        stubOwnedConversation(Conversation(MEMBER_ID))

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CONVERSATION_NOT_ENDED, exception.errorCode)
    }

    @Test
    fun `종료 후 삭제된 대화면 CONVERSATION_NOT_ENDED가 아니라 CONVERSATION_ALREADY_DELETED`() {
        val conversation = endedConversation().apply { delete() }
        stubOwnedConversation(conversation)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CONVERSATION_ALREADY_DELETED, exception.errorCode)
    }

    @Test
    fun `선점에 실패했는데 이미 카드가 있으면 LLM 호출 없이 CARD_ALREADY_EXISTS`() {
        stubOwnedConversation(endedConversation())
        every { conversationCardGenerationService.claimForCardGeneration(CONVERSATION_ID) } returns false
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns true

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_ALREADY_EXISTS, exception.errorCode)
        verify(exactly = 0) { cardMessageGenerator.generate(any(), any()) }
    }

    @Test
    fun `선점에 실패했는데 카드가 아직 없으면(동시 생성 중) LLM 호출 없이 CARD_GENERATION_IN_PROGRESS`() {
        stubOwnedConversation(endedConversation())
        every { conversationCardGenerationService.claimForCardGeneration(CONVERSATION_ID) } returns false
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns false

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_GENERATION_IN_PROGRESS, exception.errorCode)
        verify(exactly = 0) { cardMessageGenerator.generate(any(), any()) }
    }

    @Test
    fun `emotion이 없으면 유저 메시지만 보고 감정을 분류해 카드에 쓴다`() {
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        every { conversationCardGenerationService.findUserMessageContents(CONVERSATION_ID) } returns listOf("오늘 진짜 우울했다", "계속 눈물이 났다")
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
    fun `요약이 없으면 유저 메시지 원문을 카드 한 줄의 입력으로 쓴다`() {
        // 요약을 만들 수 있는 건 클라이언트뿐이라 배치는 그 값을 못 받는다. 재료인 메시지는 이미 있다(#204).
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        every { conversationCardGenerationService.findUserMessageContents(CONVERSATION_ID) } returns listOf("오늘 억울한 일이 있었어", "그래서 화가 났어")
        every {
            emotionExtractor.extract(listOf("오늘 억울한 일이 있었어", "그래서 화가 났어"))
        } returns EmotionExtractionOutput(EmotionType.ANGER, usedTokens = 5, cachedTokens = 0)
        val promptInput = slot<String>()
        every { cardMessageGenerator.generate(EmotionType.ANGER, capture(promptInput)) } returns CardMessageOutput("대사", 10, 0)
        every { cardPersistenceService.save(any(), CONVERSATION_ID, null) } answers { firstArg() }

        service.createCard(MEMBER_ID, CONVERSATION_ID, null, null)

        assertEquals("오늘 억울한 일이 있었어 / 그래서 화가 났어", promptInput.captured)
    }

    @Test
    fun `요약이 없으면 대화방 요약을 덮어쓰지 않는다`() {
        // conversations.summary는 다른 채팅방 댓글의 '과거 맥락'으로 읽히는 자리다. 압축되지 않은
        // 원문을 남기면 정보량이 많은 요약을 최근 5개 풀 밖으로 밀어낸다.
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        every { conversationCardGenerationService.findUserMessageContents(CONVERSATION_ID) } returns listOf("오늘 억울한 일이 있었어")
        every {
            emotionExtractor.extract(listOf("오늘 억울한 일이 있었어"))
        } returns EmotionExtractionOutput(EmotionType.ANGER, usedTokens = 5, cachedTokens = 0)
        every { cardMessageGenerator.generate(EmotionType.ANGER, any()) } returns CardMessageOutput("대사", 10, 0)
        every { cardPersistenceService.save(any(), CONVERSATION_ID, null) } answers { firstArg() }

        service.createCard(MEMBER_ID, CONVERSATION_ID, null, null)

        verify(exactly = 1) { cardPersistenceService.save(any(), CONVERSATION_ID, null) }
    }

    @Test
    fun `공백뿐인 요약도 없는 것과 같이 다뤄 대화방에 남기지 않는다`() {
        // LLM 입력에서만 걸러내고 저장하면, 그 방이 과거 맥락 풀(summary is not null)에 들어가
        // 내용 없이 자리만 차지한다 — 프롬프트 단계에서 공백이 걸러지기 때문이다.
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        every { conversationCardGenerationService.findUserMessageContents(CONVERSATION_ID) } returns listOf("오늘 억울한 일이 있었어")
        every {
            emotionExtractor.extract(listOf("오늘 억울한 일이 있었어"))
        } returns EmotionExtractionOutput(EmotionType.ANGER, usedTokens = 5, cachedTokens = 0)
        every { cardMessageGenerator.generate(EmotionType.ANGER, "오늘 억울한 일이 있었어") } returns CardMessageOutput("대사", 10, 0)
        every { cardPersistenceService.save(any(), CONVERSATION_ID, null) } answers { firstArg() }

        service.createCard(MEMBER_ID, CONVERSATION_ID, null, "   ")

        verify(exactly = 1) { cardPersistenceService.save(any(), CONVERSATION_ID, null) }
    }

    @Test
    fun `요약도 유저 메시지도 없으면 FAILED로 되돌리고 LLM을 부르지 않는다`() {
        // 메시지 없는 대화방은 만들어질 수 없지만, 그래도 비면 만들 재료가 없다. PENDING으로 두면
        // 재시도가 재시도 가능한 503이 아니라 409(생성 중)로 막힌다.
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        every { conversationCardGenerationService.findUserMessageContents(CONVERSATION_ID) } returns emptyList()
        stubFinishStatus(CardGenerationStatus.FAILED)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, null, null) }

        assertEquals(ErrorCode.CARD_GENERATION_FAILED, exception.errorCode)
        verify(exactly = 0) { emotionExtractor.extract(any()) }
        verify(exactly = 0) { cardMessageGenerator.generate(any(), any()) }
        verify(exactly = 1) {
            conversationCardGenerationService.finishCardGeneration(CONVERSATION_ID, CardGenerationStatus.FAILED)
        }
    }

    @Test
    fun `emotion이 있으면 감정 분류를 호출하지 않는다`() {
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        every { cardMessageGenerator.generate(EmotionType.ANGER, "요약") } returns CardMessageOutput("대사", 10, 0)
        every { cardPersistenceService.save(any(), CONVERSATION_ID, "요약") } answers { firstArg() }

        service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약")

        verify(exactly = 0) { emotionExtractor.extract(any()) }
    }

    @Test
    fun `유저 메시지가 하나도 없으면 클라이언트 요약으로 감정을 분류한다`() {
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        every { conversationCardGenerationService.findUserMessageContents(CONVERSATION_ID) } returns emptyList()
        every { emotionExtractor.extract(listOf("요약")) } returns EmotionExtractionOutput(EmotionType.JOY, usedTokens = 5, cachedTokens = 0)
        every { cardMessageGenerator.generate(EmotionType.JOY, "요약") } returns CardMessageOutput("대사", 10, 0)
        every { cardPersistenceService.save(any(), CONVERSATION_ID, "요약") } answers { firstArg() }

        service.createCard(MEMBER_ID, CONVERSATION_ID, null, "요약")

        verify(exactly = 1) { emotionExtractor.extract(listOf("요약")) }
    }

    @Test
    fun `감정 분류에 실패하면 FAILED로 전이하고 CARD_GENERATION_FAILED - 대사 생성은 호출되지 않는다`() {
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        every { conversationCardGenerationService.findUserMessageContents(CONVERSATION_ID) } returns listOf("오늘 일기")
        val extractionFailure = CardGenerationFailedException("분류 실패")
        every { emotionExtractor.extract(any()) } throws extractionFailure
        stubFinishStatus(CardGenerationStatus.FAILED)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, null, "요약") }

        assertEquals(ErrorCode.CARD_GENERATION_FAILED, exception.errorCode)
        assertEquals(extractionFailure, exception.cause)
        // 카드 경로도 댓글과 같은 재시도 보호를 받는다 — 네트워크가 한 번 튀었다고 그대로 실패하지 않는다.
        verify(exactly = 3) { emotionExtractor.extract(any()) }
        verify(exactly = 0) { cardMessageGenerator.generate(any(), any()) }
        verify(exactly = 0) { cardPersistenceService.save(any(), any(), any()) }
        verify(exactly = 1) {
            conversationCardGenerationService.finishCardGeneration(CONVERSATION_ID, CardGenerationStatus.FAILED)
        }
        verify(exactly = 1) {
            generationLogRecorder.record(
                type = GenerationType.CARD_EMOTION,
                success = false,
                attemptCount = 3,
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

    /**
     * 검증·파싱에 실패한 시도도 **호출은 됐으니 과금된다.** 카드 경로에 재시도가 붙으면서 이 합산이
     * 처음으로 여러 시도에 걸쳐 일어난다 — 마지막 한 시도만 기록하면 비용이 과소 집계된다.
     */
    @Test
    fun `감정 분류가 재시도 끝에 실패하면 시도마다의 토큰을 합산해 기록한다`() {
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        every { conversationCardGenerationService.findUserMessageContents(CONVERSATION_ID) } returns listOf("오늘 일기")
        every { emotionExtractor.extract(any()) } throwsMany
            listOf(
                CardGenerationFailedException("1차", usedTokens = 100, cachedTokens = 10, inputTokens = 80, outputTokens = 20),
                CardGenerationFailedException("2차", usedTokens = 200, cachedTokens = 20, inputTokens = 150, outputTokens = 50),
                CardGenerationFailedException("3차", usedTokens = 400, cachedTokens = 30, inputTokens = 300, outputTokens = 100),
            )
        stubFinishStatus(CardGenerationStatus.FAILED)

        assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, null, "요약") }

        verify(exactly = 1) {
            generationLogRecorder.record(
                type = GenerationType.CARD_EMOTION,
                success = false,
                attemptCount = 3,
                latencyMs = any(),
                memberId = MEMBER_ID,
                conversationId = CONVERSATION_ID,
                usedTokens = 700,
                cachedTokens = 60,
                inputTokens = 530,
                outputTokens = 170,
                failureReason = any(),
            )
        }
    }

    @Test
    fun `분류용 메시지 조회가 실패해도 FAILED로 전이하고 CARD_GENERATION_FAILED - PENDING으로 남지 않는다`() {
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        val readFailure = QueryTimeoutException("조회 타임아웃")
        every { conversationCardGenerationService.findUserMessageContents(CONVERSATION_ID) } throws readFailure
        stubFinishStatus(CardGenerationStatus.FAILED)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, null, "요약") }

        assertEquals(ErrorCode.CARD_GENERATION_FAILED, exception.errorCode)
        assertEquals(readFailure, exception.cause)
        verify(exactly = 0) { emotionExtractor.extract(any()) }
        verify(exactly = 1) {
            conversationCardGenerationService.finishCardGeneration(CONVERSATION_ID, CardGenerationStatus.FAILED)
        }
    }

    @Test
    fun `대사 생성에 실패하면 FAILED로 전이하고 CARD_GENERATION_FAILED`() {
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        val generationFailure = CardGenerationFailedException("실패")
        every { cardMessageGenerator.generate(any(), any()) } throws generationFailure
        stubFinishStatus(CardGenerationStatus.FAILED)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_GENERATION_FAILED, exception.errorCode)
        assertEquals(generationFailure, exception.cause)
        verify(exactly = 0) { cardPersistenceService.save(any(), any(), any()) }
        verify(exactly = 1) {
            conversationCardGenerationService.finishCardGeneration(CONVERSATION_ID, CardGenerationStatus.FAILED)
        }
    }

    /**
     * 재시도 대상이 아닌 예외(SDK 결함 등)로 중단돼도 상태는 되돌아가야 한다. PENDING으로 남으면
     * 사용자의 재시도가 재시도 가능한 503이 아니라 409(생성 중)로 막힌다 — 정리 스케줄러가
     * 타임아웃시킬 때까지다. 실패 로그가 빠지면 실패율·비용 집계도 함께 샌다.
     */
    @Test
    fun `감정 분류가 재시도 대상이 아닌 예외로 끊겨도 FAILED로 전이하고 실패 로그를 남긴다`() {
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        every { conversationCardGenerationService.findUserMessageContents(CONVERSATION_ID) } returns listOf("오늘 일기")
        val sdkFailure = IllegalStateException("SDK 응답에 후보가 없다")
        every { emotionExtractor.extract(any()) } throws sdkFailure
        stubFinishStatus(CardGenerationStatus.FAILED)

        // 업무 오류로 위장하지 않고 원래 예외를 그대로 올린다.
        val thrown = assertFailsWith<IllegalStateException> { service.createCard(MEMBER_ID, CONVERSATION_ID, null, "요약") }

        assertEquals(sdkFailure, thrown)
        // 재시도 대상이 아니므로 다시 부르지 않는다.
        verify(exactly = 1) { emotionExtractor.extract(any()) }
        verify(exactly = 0) { cardMessageGenerator.generate(any(), any()) }
        verify(exactly = 1) {
            conversationCardGenerationService.finishCardGeneration(CONVERSATION_ID, CardGenerationStatus.FAILED)
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
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        val sdkFailure = IllegalStateException("SDK 응답에 후보가 없다")
        every { cardMessageGenerator.generate(any(), any()) } throws sdkFailure
        stubFinishStatus(CardGenerationStatus.FAILED)

        val thrown =
            assertFailsWith<IllegalStateException> {
                service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약")
            }

        assertEquals(sdkFailure, thrown)
        verify(exactly = 1) { cardMessageGenerator.generate(any(), any()) }
        verify(exactly = 0) { cardPersistenceService.save(any(), any(), any()) }
        verify(exactly = 1) {
            conversationCardGenerationService.finishCardGeneration(CONVERSATION_ID, CardGenerationStatus.FAILED)
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
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        every { cardMessageGenerator.generate(any(), any()) } returns CardMessageOutput("대사", 10, 0)
        val saveFailure = DataIntegrityViolationException("duplicate")
        every { cardPersistenceService.save(any(), any(), any()) } throws saveFailure
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns true
        stubFinishStatus(CardGenerationStatus.DONE)

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CARD_ALREADY_EXISTS, exception.errorCode)
        assertEquals(saveFailure, exception.cause)
        verify(exactly = 1) {
            conversationCardGenerationService.finishCardGeneration(CONVERSATION_ID, CardGenerationStatus.DONE)
        }
    }

    @Test
    fun `저장 시점 유니크 위반인데 실제로는 카드가 없으면 FAILED로 전이하고 원래 예외를 그대로 던진다`() {
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        every { cardMessageGenerator.generate(any(), any()) } returns CardMessageOutput("대사", 10, 0)
        val saveFailure = DataIntegrityViolationException("not-null constraint")
        every { cardPersistenceService.save(any(), any(), any()) } throws saveFailure
        every { cardRepository.existsByConversationId(CONVERSATION_ID) } returns false
        stubFinishStatus(CardGenerationStatus.FAILED)

        val exception =
            assertFailsWith<DataIntegrityViolationException> {
                service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약")
            }

        assertEquals(saveFailure, exception)
        verify(exactly = 1) {
            conversationCardGenerationService.finishCardGeneration(CONVERSATION_ID, CardGenerationStatus.FAILED)
        }
    }

    /**
     * 저장 실패도 CAS 선점 이후다. PENDING 으로 남기면 사용자의 재시도가 재시도 가능한 503 이 아니라
     * 409(생성 중)로 막힌다 — 정리 스케줄러가 타임아웃시킬 때까지다.
     */
    @Test
    fun `저장이 유니크 위반도 상태 충돌도 아닌 이유로 실패해도 FAILED로 전이한다`() {
        stubOwnedConversation(endedConversation())
        stubClaimSuccess()
        every { cardMessageGenerator.generate(any(), any()) } returns CardMessageOutput("대사", 10, 0)
        val saveFailure = QueryTimeoutException("저장 타임아웃")
        every { cardPersistenceService.save(any(), any(), any()) } throws saveFailure
        stubFinishStatus(CardGenerationStatus.FAILED)

        // 업무 오류로 위장하지 않고 원래 예외를 그대로 올린다.
        val exception =
            assertFailsWith<QueryTimeoutException> {
                service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약")
            }

        assertEquals(saveFailure, exception)
        verify(exactly = 1) {
            conversationCardGenerationService.finishCardGeneration(CONVERSATION_ID, CardGenerationStatus.FAILED)
        }
    }

    @Test
    fun `저장 시점 상태 전이가 채팅방 삭제로 실패하면 CONVERSATION_ALREADY_DELETED로 변환된다`() {
        val activeConversation = endedConversation()
        val deletedConversation = endedConversation().apply { delete() }
        stubOwnedConversation(activeConversation)
        every { conversationCardGenerationService.findConversation(CONVERSATION_ID) } returns deletedConversation
        stubClaimSuccess()
        every { cardMessageGenerator.generate(any(), any()) } returns CardMessageOutput("대사", 10, 0)
        every {
            cardPersistenceService.save(any(), any(), any())
        } throws CardGenerationStateConflictException("conflict")

        val exception = assertFailsWith<BusinessException> { service.createCard(MEMBER_ID, CONVERSATION_ID, EmotionType.ANGER, "요약") }

        assertEquals(ErrorCode.CONVERSATION_ALREADY_DELETED, exception.errorCode)
        // 이미 PENDING이 아니라는 뜻(삭제됨)이라 되돌릴 대상 자체가 없다 — 되돌리기를 시도하지 않는다.
        verify(exactly = 0) {
            conversationCardGenerationService.finishCardGeneration(CONVERSATION_ID, CardGenerationStatus.FAILED)
        }
    }

    @Test
    fun `저장 시점 상태 전이가 삭제 아닌 이유로 실패하면(PENDING 타임아웃 리셋 등) CARD_GENERATION_FAILED로 변환된다`() {
        stubOwnedConversation(endedConversation())
        // 전이 실패 뒤 다시 읽었을 때 방이 살아 있다 = 삭제가 아닌 다른 이유로 PENDING 이 풀린 것이다.
        every { conversationCardGenerationService.findConversation(CONVERSATION_ID) } returns endedConversation()
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
