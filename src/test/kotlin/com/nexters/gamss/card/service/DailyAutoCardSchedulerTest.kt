package com.nexters.gamss.card.service

import com.nexters.gamss.card.config.CardProperties
import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.service.ConversationService
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.service.MemberService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyAutoCardSchedulerTest {
    private val conversationRepository = mockk<ConversationRepository>()
    private val conversationService = mockk<ConversationService>()
    private val memberService = mockk<MemberService> { every { getById(any()) } returns Member() }
    private val cardService = mockk<CardService>()
    private val properties = CardProperties(autoCardStartDate = LocalDate.of(2026, 8, 20))
    private val scheduler =
        DailyAutoCardScheduler(conversationRepository, conversationService, memberService, cardService, properties)

    private val createdAfter: Instant = Instant.parse("2026-08-19T15:00:00Z")
    private val createdBefore: Instant = Instant.parse("2026-08-25T15:00:00Z")

    private fun conversation(
        memberId: Long = MEMBER_ID,
        summary: String? = "오늘 억울한 일이 있었다",
    ): Conversation = Conversation(memberId).apply { summary?.let { updateSummary(it) } }

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

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 1) { conversationService.endForAutoBatch(10L) }
        verify(exactly = 1) { conversationService.endForAutoBatch(20L) }
        verify(exactly = 0) { cardService.createCard(any(), any(), any(), any()) }
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
    fun `스케줄 진입점은 설정한 시작일부터 KST 오늘 자정까지 만들어진 대화방을 대상으로 삼는다`() {
        val capturedAfter = slot<Instant>()
        val capturedBefore = slot<Instant>()
        every {
            conversationRepository.findAutoCardTargetIds(capture(capturedAfter), capture(capturedBefore), any(), any())
        } returns emptyList()

        scheduler.autoEndAndCreateCards()

        val zone = ZoneId.of("Asia/Seoul")
        // 하한은 요약 저장이 배포된 날(설정값), 상한은 오늘 자정 — 오늘 기록은 오늘 밤까지 열어둔다.
        assertEquals(LocalDate.of(2026, 8, 20).atStartOfDay(zone).toInstant(), capturedAfter.captured)
        assertEquals(LocalDate.now(zone).atStartOfDay(zone).toInstant(), capturedBefore.captured)
    }

    companion object {
        private const val MEMBER_ID = 1L
        private const val OTHER_MEMBER_ID = 2L
    }
}
