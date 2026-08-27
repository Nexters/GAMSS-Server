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
import com.nexters.gamss.notification.domain.NotificationOutcome
import com.nexters.gamss.notification.domain.NotificationType
import com.nexters.gamss.notification.push.PushSendResult
import com.nexters.gamss.notification.service.CardCreatedNotifier
import com.nexters.gamss.notification.service.NotificationLogRecorder
import com.nexters.gamss.notification.service.PushInTransactionException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DailyAutoCardSchedulerTest {
    private val conversationRepository = mockk<ConversationRepository>()
    private val conversationService = mockk<ConversationService>()
    private val memberService = mockk<MemberService> { every { getById(any()) } returns Member() }
    private val cardService = mockk<CardService>()
    private val cardCreatedNotifier = mockk<CardCreatedNotifier>(relaxed = true)
    private val notificationLogRecorder = mockk<NotificationLogRecorder>(relaxed = true)
    private val window = AutoCardWindow(CardProperties(autoCardStartDate = START_DATE))
    private val meterRegistry = SimpleMeterRegistry()
    private val scheduler =
        DailyAutoCardScheduler(
            conversationRepository,
            conversationService,
            memberService,
            cardService,
            window,
            meterRegistry,
            cardCreatedNotifier,
            notificationLogRecorder,
        )

    private val createdAfter: Instant = Instant.parse("2026-08-19T15:00:00Z")
    private val createdBefore: Instant = Instant.parse("2026-08-25T15:00:00Z")

    @Test
    fun `카드를 만든 회원에게 알림을 보낸다`() {
        stubTargets(10L)
        every { conversationService.endForAutoBatch(10L) } returns conversation()
        every { cardService.createCard(any(), any(), any(), any()) } returns mockk<Card>()

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 1) { cardCreatedNotifier.notifyCardCreated(MEMBER_ID) }
    }

    /**
     * 한 사람이 방을 여러 개 만들면 카드도 여러 장 나온다. 그대로 두면 새벽에 푸시가 연달아 간다.
     */
    @Test
    fun `한 회원이 카드를 여러 장 받아도 알림은 한 번만 간다`() {
        stubTargets(10L, 20L, 30L)
        every { conversationService.endForAutoBatch(any()) } returns conversation()
        every { cardService.createCard(any(), any(), any(), any()) } returns mockk<Card>()

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 1) { cardCreatedNotifier.notifyCardCreated(MEMBER_ID) }
    }

    /**
     * 알림이 실제로 나갔는지를 백오피스가 대화방별로 보여준다. 발송은 회원당 한 번이라, 건너뛴 방을
     * 기록하지 않으면 "대상이었지만 다른 방으로 이미 나갔다"와 "애초에 대상이 아니었다"가 표에서
     * 똑같이 빈칸으로 보인다.
     */
    @Test
    fun `건너뛴 방도 SKIPPED 로 기록한다`() {
        stubTargets(10L, 20L)
        every { conversationService.endForAutoBatch(any()) } returns conversation()
        every { cardService.createCard(any(), any(), any(), any()) } returns mockk<Card>()
        val sent = PushSendResult(successCount = 1, failureCount = 0, invalidTokens = emptyList())
        every { cardCreatedNotifier.notifyCardCreated(MEMBER_ID) } returns sent

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 1) {
            notificationLogRecorder.record(MEMBER_ID, listOf(10L), NotificationType.CARD_CREATED, sent)
        }
        verify(exactly = 1) {
            notificationLogRecorder.record(MEMBER_ID, listOf(20L), NotificationType.CARD_CREATED, NotificationOutcome.SKIPPED)
        }
    }

    /** 카드가 안 만들어진 방은 알림 대상이 아니므로 기록도 남지 않는다(표에서 빈칸). */
    @Test
    fun `카드를 못 만든 방은 기록하지 않는다`() {
        stubTargets(10L)
        every { conversationService.endForAutoBatch(10L) } returns null

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 0) { notificationLogRecorder.record(any(), any(), any(), any<NotificationOutcome>()) }
        verify(exactly = 0) { notificationLogRecorder.record(any(), any(), any(), any<PushSendResult>()) }
    }

    @Test
    fun `회원이 다르면 각각 알린다`() {
        stubTargets(10L, 20L)
        every { conversationService.endForAutoBatch(10L) } returns conversation(memberId = MEMBER_ID)
        every { conversationService.endForAutoBatch(20L) } returns conversation(memberId = OTHER_MEMBER_ID)
        every { cardService.createCard(any(), any(), any(), any()) } returns mockk<Card>()

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 1) { cardCreatedNotifier.notifyCardCreated(MEMBER_ID) }
        verify(exactly = 1) { cardCreatedNotifier.notifyCardCreated(OTHER_MEMBER_ID) }
    }

    /**
     * 가드 예외는 삼키지 않고 배치를 중단시킨다. 그 상태로 계속 돌면 DB 커넥션을 붙잡은 채 LLM 을
     * 수백 번 부르기 때문이다. 루프의 다른 줄은 전부 예외를 삼키고 있어서, 나중에 여기도 감싸는 것이
     * 개선처럼 보일 수 있다. 그러면 이 대가가 조용히 사라진다.
     */
    @Test
    fun `알림이 트랜잭션 가드에 걸리면 배치를 중단한다`() {
        stubTargets(10L, 20L, 30L)
        every { conversationService.endForAutoBatch(any()) } returns conversation()
        every { cardService.createCard(any(), any(), any(), any()) } returns mockk<Card>()
        every { cardCreatedNotifier.notifyCardCreated(any()) } throws PushInTransactionException("트랜잭션 안")

        assertFailsWith<PushInTransactionException> { scheduler.runFor(createdAfter, createdBefore) }

        // 첫 카드에서 멈추므로 뒤쪽 방은 손대지 않는다.
        verify(exactly = 1) { cardService.createCard(any(), any(), any(), any()) }
        // 멈추기 전에 만든 카드는 이미 커밋됐다. 지표가 0 이면 아무 일 없던 날과 구별되지 않는다.
        assertEquals(1.0, outcomeCount(AutoCardOutcome.CREATED))
    }

    /** 카드를 못 만든 결과들이다. 이 사람들에게 "카드가 도착했어요"가 가면 안 된다. */
    @Test
    fun `카드를 만들지 못하면 알림을 보내지 않는다`() {
        stubTargets(10L, 20L)
        stubMarkSkipped()
        every { conversationService.endForAutoBatch(10L) } returns conversation(summary = null)
        every { conversationService.endForAutoBatch(20L) } returns null

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 0) { cardCreatedNotifier.notifyCardCreated(any()) }
    }

    private fun outcomeCount(outcome: AutoCardOutcome): Double =
        meterRegistry
            .get("gamss.autocard.outcome")
            .tag("outcome", outcome.name)
            .counter()
            .count()

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

        // emotion을 null로 넘겨 서버가 유저 메시지로 분류하게 한다. 배치엔 클라이언트가 없다.
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
    fun `요약이 없어도 카드는 만든다`() {
        // 한 줄만 쓰고 나간 방에는 프론트가 보내주는 요약이 없다. 그 방이 가장 흔한 이탈 패턴이라
        // 여기서 포기하면 자동 카드를 못 받는 방의 대부분이 그쪽이 된다(#204).
        stubTargets(10L, 20L)
        every { conversationService.endForAutoBatch(10L) } returns conversation(summary = null)
        every { conversationService.endForAutoBatch(20L) } returns conversation(summary = "   ")
        every { cardService.createCard(any(), any(), any(), any()) } returns mockk<Card>()

        scheduler.runFor(createdAfter, createdBefore)

        // 요약을 판정하지 않고 그대로 넘긴다. 원문으로 대체할지는 카드 생성 경로가 정한다.
        verify(exactly = 1) { cardService.createCard(MEMBER_ID, 10L, null, null) }
        verify(exactly = 1) { cardService.createCard(MEMBER_ID, 20L, null, "   ") }
    }

    @Test
    fun `요약이 없다는 이유로 대화방 상태를 건드리지 않는다`() {
        // SKIPPED로 못 박던 자리다. 다시 박으면 그 방이 다음 실행 대상에서 빠져 카드를 영영 못 받는다.
        stubTargets(10L)
        every { conversationService.endForAutoBatch(10L) } returns conversation(summary = null)
        every { cardService.createCard(any(), any(), any(), any()) } returns mockk<Card>()

        scheduler.runFor(createdAfter, createdBefore)

        verify(exactly = 0) { conversationRepository.updateCardGenerationStatus(any(), any(), any(), any()) }
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
        // 카드가 없으니 "카드가 도착했어요" 도 가면 안 된다. 탈퇴하면 기기 토큰도 지워지지만
        // (DeviceTokenCleaner) 방어선이 그것 하나뿐인 상태로 두지 않는다.
        verify(exactly = 0) { cardCreatedNotifier.notifyCardCreated(MEMBER_ID) }
        verify(exactly = 1) { cardCreatedNotifier.notifyCardCreated(OTHER_MEMBER_ID) }
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

        // 호출을 시각 구간으로 감싼다. 호출 도중 05시 경계가 지나가도(하루 한 순간) 검증이
        // 흔들리지 않게, 단언은 이 구간에 대해 성립하는 성질만 본다.
        val zone = ZoneId.of("Asia/Seoul")
        val before = ZonedDateTime.now(zone)
        scheduler.autoEndAndCreateCards()
        val after = ZonedDateTime.now(zone)

        // 하한은 요약 저장이 배포된 날의 하루 시작(05시).
        val startDayBegin = START_DATE.atTime(5, 0).atZone(zone)
        assertEquals(startDayBegin.toInstant(), capturedAfter.captured)

        // 상한은 자정이 아니라 하루 경계여야 한다. 자정을 쓰면 0~5시에 만든 방이 어제에 속하는데도
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
