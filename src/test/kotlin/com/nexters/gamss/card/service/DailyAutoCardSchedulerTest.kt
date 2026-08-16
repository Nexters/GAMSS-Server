package com.nexters.gamss.card.service

import com.nexters.gamss.card.config.CardProperties
import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.service.ConversationService
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.service.MemberService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyAutoCardSchedulerTest {
    private val conversationRepository = mockk<ConversationRepository>()
    private val conversationService = mockk<ConversationService>()
    private val memberService = mockk<MemberService> { every { getById(any()) } returns Member() }
    private val cardService = mockk<CardService>()
    private val properties = CardProperties(autoCardStartDate = START_DATE)
    private val scheduler =
        DailyAutoCardScheduler(
            conversationRepository,
            conversationService,
            memberService,
            cardService,
            properties,
            SimpleMeterRegistry(),
        )

    private val createdAfter: Instant = Instant.parse("2026-08-19T15:00:00Z")
    private val createdBefore: Instant = Instant.parse("2026-08-25T15:00:00Z")

    private fun conversation(
        memberId: Long = MEMBER_ID,
        summary: String? = "오늘 억울한 일이 있었다",
    ): Conversation = Conversation(memberId).apply { summary?.let { updateSummary(it) } }

    private fun stubMarkSkipped() {
        every { conversationRepository.updateCardGenerationStatus(any(), any(), any(), any()) } returns 1
    }

    private fun stubTargets(vararg ids: Long) {
        every { conversationRepository.findAutoCardTargetIds(any(), any(), any(), any()) } returns ids.toList()
    }

    @Test
    fun `대상 대화방을 종료하고 emotion 없이 카드를 만든다`() {
        stubTargets(10L)
        every { conversationService.endForAutoBatch(10L) } returns conversation()
        every { cardService.createCard(any(), any(), any(), any()) } returns mockk<Card>()

        scheduler.runFor(createdAfter, createdBefore)

        // emotion을 null로 넘겨 서버가 유저 메시지로 분류하게 한다 — 배치엔 클라이언트가 없다.
        verify(exactly = 1) { cardService.createCard(MEMBER_ID, 10L, null, "오늘 억울한 일이 있었다") }
    }

    @Test
    fun `한 대화방이 실패해도 나머지 대화방은 계속 처리한다`() {
        stubTargets(10L, 20L, 30L)
        every { conversationService.endForAutoBatch(any()) } returns conversation()
        every { cardService.createCard(any(), 20L, any(), any()) } throws IllegalStateException("예상 못 한 실패")
        every { cardService.createCard(any(), 10L, any(), any()) } returns mockk<Card>()
        every { cardService.createCard(any(), 30L, any(), any()) } returns mockk<Card>()

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 1) { cardService.createCard(any(), 10L, any(), any()) }
        verify(exactly = 1) { cardService.createCard(any(), 30L, any(), any()) }
    }

    @Test
    fun `요약이 없으면 종료만 하고 카드는 만들지 않는다`() {
        stubTargets(10L, 20L)
        every { conversationService.endForAutoBatch(10L) } returns conversation(summary = null)
        every { conversationService.endForAutoBatch(20L) } returns conversation(summary = "   ")
        every { cardService.createCard(any(), any(), any(), any()) } returns mockk<Card>()
        stubMarkSkipped()

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 1) { conversationService.endForAutoBatch(10L) }
        verify(exactly = 1) { conversationService.endForAutoBatch(20L) }
        verify(exactly = 0) { cardService.createCard(any(), any(), any(), any()) }
    }

    @Test
    fun `요약이 없는 방은 자동 생성을 포기했다고 표시해 다음 실행에서 다시 잡지 않는다`() {
        // 종료된 방에는 메시지를 못 보내니 요약이 채워질 길이 없다 — 상태로 못 박지 않으면
        // 결론이 같은 방을 매일 밤 다시 집는다.
        stubTargets(10L)
        every { conversationService.endForAutoBatch(10L) } returns conversation(summary = null)
        stubMarkSkipped()

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 1) {
            conversationRepository.updateCardGenerationStatus(
                10L,
                CardGenerationStatus.SKIPPED,
                listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED),
                any(),
            )
        }
    }

    @Test
    fun `삭제된 대화방은 카드 생성 대상에서 제외된다`() {
        stubTargets(10L)
        every { conversationService.endForAutoBatch(10L) } returns null

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 0) { cardService.createCard(any(), any(), any(), any()) }
    }

    @Test
    fun `탈퇴한 회원의 대화방에는 카드를 만들지 않는다`() {
        stubTargets(10L, 20L)
        every { conversationService.endForAutoBatch(any()) } returns conversation()
        every { memberService.getById(MEMBER_ID) } returns Member().apply { withdraw() }
        every { memberService.getById(OTHER_MEMBER_ID) } returns Member()
        every { conversationService.endForAutoBatch(20L) } returns conversation(memberId = OTHER_MEMBER_ID)
        every { cardService.createCard(any(), any(), any(), any()) } returns mockk<Card>()

        scheduler.runFor(createdAfter, createdBefore)

        // 탈퇴 후에는 그 사람의 대화로 새 카드를 만들지 않는다.
        verify(exactly = 0) { cardService.createCard(MEMBER_ID, any(), any(), any()) }
        verify(exactly = 1) { cardService.createCard(OTHER_MEMBER_ID, 20L, any(), any()) }
    }

    @Test
    fun `이미 카드가 있거나 생성 중이면 삼키고 다음 대화방을 처리한다`() {
        stubTargets(10L, 20L, 30L)
        every { conversationService.endForAutoBatch(any()) } returns conversation()
        every {
            cardService.createCard(any(), 10L, any(), any())
        } throws BusinessException(ErrorCode.CARD_ALREADY_EXISTS)
        every {
            cardService.createCard(any(), 20L, any(), any())
        } throws BusinessException(ErrorCode.CARD_GENERATION_IN_PROGRESS)
        every { cardService.createCard(any(), 30L, any(), any()) } returns mockk<Card>()

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 1) { cardService.createCard(any(), 30L, any(), any()) }
    }

    @Test
    fun `토큰 상한에 걸린 대화방은 건너뛰고 나머지를 처리한다`() {
        stubTargets(10L, 20L)
        every { conversationService.endForAutoBatch(any()) } returns conversation()
        every {
            cardService.createCard(any(), 10L, any(), any())
        } throws BusinessException(ErrorCode.DAILY_TOKEN_LIMIT_EXCEEDED)
        every { cardService.createCard(any(), 20L, any(), any()) } returns mockk<Card>()

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 1) { cardService.createCard(any(), 20L, any(), any()) }
    }

    @Test
    fun `대상이 없으면 아무것도 하지 않는다`() {
        stubTargets()

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 0) { conversationService.endForAutoBatch(any()) }
        verify(exactly = 0) { cardService.createCard(any(), any(), any(), any()) }
    }

    @Test
    fun `스케줄 진입점은 설정한 시작일부터 가장 최근 하루 경계까지를 대상으로 삼는다`() {
        val capturedAfter = slot<Instant>()
        val capturedBefore = slot<Instant>()
        every {
            conversationRepository.findAutoCardTargetIds(capture(capturedAfter), capture(capturedBefore), any(), any())
        } returns emptyList()

        // 호출을 시각 구간으로 감싼다 — 호출 도중 05시 경계가 지나가도(하루 한 순간) 검증이
        // 흔들리지 않게, 단언은 이 구간에 대해 성립하는 성질만 본다.
        val zone = ZoneId.of("Asia/Seoul")
        val before = ZonedDateTime.now(zone)
        scheduler.autoEndAndCreateCards()
        val after = ZonedDateTime.now(zone)

        // 하한은 요약 저장이 배포된 날의 하루 시작(05시).
        val startDayBegin = START_DATE.atTime(5, 0).atZone(zone)
        assertEquals(startDayBegin.toInstant(), capturedAfter.captured)

        // 상한은 자정이 아니라 하루 경계여야 한다 — 자정을 쓰면 0~5시에 만든 방이 어제에 속하는데도
        // "오늘 것"으로 분류돼 하루를 더 열린 채로 기다린다.
        val boundary = capturedBefore.captured.atZone(zone)
        assertEquals(5, boundary.hour)
        assertEquals(0, boundary.minute)
        // 그리고 이미 지난 경계여야 한다(미래 경계를 쓰면 아직 진행 중인 하루까지 닫아버린다).
        assertTrue(boundary <= after, "이미 지난 하루 경계여야 한다: $boundary")
        assertTrue(boundary > before.minusDays(1), "가장 최근 경계여야 한다: $boundary")
        // 시작일이 과거이므로 조회 구간이 뒤집히지 않는다(뒤집히면 대상이 늘 비어 조용히 아무 일도 안 한다).
        assertTrue(capturedAfter.captured < capturedBefore.captured, "조회 구간이 뒤집히면 안 된다")
    }

    companion object {
        /** 배치가 다루기 시작한 날. 조회 구간이 뒤집히지 않도록 늘 과거인 날짜를 쓴다. */
        private val START_DATE: LocalDate = LocalDate.of(2026, 1, 1)

        private const val MEMBER_ID = 1L
        private const val OTHER_MEMBER_ID = 2L
    }
}
