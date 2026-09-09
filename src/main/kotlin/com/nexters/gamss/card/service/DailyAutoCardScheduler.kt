package com.nexters.gamss.card.service

import com.nexters.gamss.conversation.service.ConversationCardGenerationService
import com.nexters.gamss.conversation.service.ConversationService
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.notification.domain.NotificationOutcome
import com.nexters.gamss.notification.domain.NotificationType
import com.nexters.gamss.notification.service.CardCreatedNotifier
import com.nexters.gamss.notification.service.NotificationLogRecorder
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 사용자가 종료 버튼을 누르지 않아 아직 열려 있는 어제까지의 대화방을 매일 새벽 자동으로 종료하고
 * 카드를 만든다. 종료 버튼을 안 눌렀다는 이유로 그날 기록이 카드로 남지 않는 것을 막는다.
 *
 * 대화방 하나의 실패가 나머지를 막지 않도록 방 단위로 예외를 삼키고, 처리 결과만 집계해 남긴다.
 * 중복 실행과 재시도에 대한 멱등성은 이 클래스가 아니라 카드 생성 경로가 보장한다. CAS 선점과
 * `cards.conversation_id` 유니크 제약에 걸린 요청은 여기서 "이미 처리됨"으로 분류된다.
 *
 * 요약([com.nexters.gamss.conversation.domain.Conversation.summary])이 없는 방도 카드를 만든다.
 * 요약은 프론트가 메시지마다 보내주는 값이라 한 줄만 쓰고 나간 방에는 없는데, 그게 가장 흔한 이탈
 * 패턴이라 그대로 두면 카드를 못 받는 방의 대부분이 그쪽이 된다(#204). 배치에는 클라이언트가
 * 없으므로 유저가 보낸 메시지 원문을 요약 대신 넣는다([CardService.createCard]).
 *
 * 대상은 [com.nexters.gamss.card.config.CardProperties.autoCardStartDate] 이후에 만들어진 방으로
 * 제한한다. 그 전 방들도 이제는 카드를 만들 재료가 있지만, 한참 지나 잊힌 대화를 새삼 종료하고
 * 카드로 되살릴 이유가 없다.
 */
@Component
class DailyAutoCardScheduler(
    private val conversationCardGenerationService: ConversationCardGenerationService,
    private val conversationService: ConversationService,
    private val memberService: MemberService,
    private val cardService: CardService,
    private val window: AutoCardWindow,
    private val meterRegistry: MeterRegistry,
    private val cardCreatedNotifier: CardCreatedNotifier,
    private val notificationLogRecorder: NotificationLogRecorder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 배치 1회의 소요 시간. 이 배치는 새벽 5시에 하루 치 방을 한꺼번에 돌며 방마다 LLM 을 호출하므로,
     * 대상이 늘면 소요 시간이 선형으로 늘어난다. 다음 스케줄까지 안 끝나는 상황을 미리 보기 위한 값이다.
     * 로그에도 결과가 남지만 로그는 임계치 알림을 걸 수 없다.
     *
     * **카드 생성 알림(FCM 왕복)도 이 시간에 포함된다.** 루프 안에서 보내기 때문이다. 배치가 실제로
     * 붙잡고 있는 시간이라는 뜻에서는 맞지만, 이 값이 늘었을 때 LLM 때문인지 발송 때문인지는 이
     * 지표만으로 갈리지 않는다(대시보드 "5 · LLM 생성"이 이 값을 쓴다).
     */
    private val batchTimer =
        Timer
            .builder("gamss.autocard.batch")
            .description("자동 카드 생성 배치 1회 소요 시간")
            .register(meterRegistry)

    /**
     * 결과별 카운터를 미리 0 으로 등록해 둔다. 처음 발생할 때 만들면 그 시계열이 '없다가 생긴' 것이
     * 되는데, increase() 는 구간의 첫 값을 기준으로 삼아 그 증가를 세지 않는다. 즉 FAILED 가 처음
     * 난 날의 알림이 조용히 빠진다. 게이지를 init 에서 등록하는 것과 같은 이유다.
     */
    private val outcomeCounters: Map<AutoCardOutcome, Counter> =
        AutoCardOutcome.entries.associateWith { outcome ->
            Counter
                .builder("gamss.autocard.outcome")
                .description("자동 카드 생성 배치가 처리한 대화방 수(결과별)")
                .tag("outcome", outcome.name)
                .register(meterRegistry)
        }

    @Scheduled(cron = CRON, zone = AutoCardWindow.ZONE_ID)
    fun autoEndAndCreateCards() {
        runFor(
            createdAfter = window.createdAfter(),
            createdBefore = window.createdBefore(),
        )
    }

    /**
     * [createdAfter]와 [createdBefore] 사이에 만들어진 대상들을 처리한다. 스케줄 진입점과 분리해
     * 둔 것은 테스트가 기준 시각을 직접 주기 위해서다.
     */
    fun runFor(
        createdAfter: Instant,
        createdBefore: Instant,
    ) {
        // 대상이 없어 일찍 끝나는 실행도 시간에 포함한다. '배치가 아예 안 돌았다'와
        // '돌았는데 대상이 없었다'는 다른 상황이고, 타이머 count 가 그 둘을 갈라준다.
        val started = Timer.start(meterRegistry)
        // 집계는 try 밖에 둔다. 루프가 중간에 끊겨도(알림의 트랜잭션 가드) 그때까지 처리한 방들은
        // 이미 커밋돼 있어, 한 일이 지표에도 로그에도 안 남으면 아무 일 없던 날과 구별되지 않는다.
        val tally = RunTally()
        try {
            val targetIds = conversationCardGenerationService.findAutoCardTargetIds(createdAfter, createdBefore)
            if (targetIds.isEmpty()) {
                log.info("자동 카드 생성 배치: 대상 없음 (기준={}~{})", createdAfter, createdBefore)
                return
            }
            tally.targetCount = targetIds.size
            targetIds.forEach { processAndNotify(it, tally) }
            tally.completed = true
        } finally {
            started.stop(batchTimer)
            record(tally)
        }
    }

    /** 방 하나를 처리하고, 결과에 따라 알리거나 기록만 남긴다. 집계는 [tally] 에 쌓는다. */
    private fun processAndNotify(
        conversationId: Long,
        tally: RunTally,
    ) {
        val result = process(conversationId)
        tally.counts.merge(result.outcome, 1, Int::plus)
        when (result.outcome) {
            AutoCardOutcome.CREATED -> notifyCreated(conversationId, checkNotNull(result.memberId), tally)

            AutoCardOutcome.ALREADY_HANDLED -> recordAlreadyHandled(conversationId, checkNotNull(result.memberId))

            // SKIPPED_DELETED · WITHDRAWN_MEMBER · FAILED 는 기록하지 않는다. 삭제된 방은 백오피스가
            // 상태(DELETED)만으로 이미 구분하고, 탈퇴·실패는 이 배치가 아니라 다음 실행이 다시 본다.
            else -> Unit
        }
    }

    /**
     * 새로 카드가 생긴 회원에게 알린다. 한 사람이 방을 여러 개 만들면 카드도 여러 장 나오는데,
     * 그대로 두면 새벽에 푸시가 연달아 가므로 이번 실행에서 이미 알린 회원은 건너뛴다(add 가
     * false 를 돌려준다).
     */
    private fun notifyCreated(
        conversationId: Long,
        memberId: Long,
        tally: RunTally,
    ) {
        if (!tally.notifiedMemberIds.add(memberId)) {
            // 건너뛴 것도 남긴다. 백오피스에서 "대상이었지만 다른 방으로 이미 나갔다"와 "애초에
            // 대상이 아니었다"(기록 없음)가 구분돼야 한다.
            notificationLogRecorder.record(memberId, listOf(conversationId), NotificationType.CARD_CREATED, NotificationOutcome.SKIPPED)
            return
        }
        val sent = cardCreatedNotifier.notifyCardCreated(memberId)
        notificationLogRecorder.record(memberId, listOf(conversationId), NotificationType.CARD_CREATED, sent)
        tally.notifiedSuccessCount += sent.successCount
        tally.notifiedFailureCount += sent.failureCount
    }

    /**
     * 이 방의 카드는 이미 다른 경로(주로 사용자의 수동 종료+생성)로 만들어져 있었다. 새로 보낼
     * 알림은 없지만, 이 사실 자체를 남기지 않으면 백오피스가 "배치가 실제로 봤는데 할 일이
     * 없었다"와 "배치가 애초에 보지도 않았다"를 구분하지 못한다.
     */
    private fun recordAlreadyHandled(
        conversationId: Long,
        memberId: Long,
    ) {
        notificationLogRecorder.record(memberId, listOf(conversationId), NotificationType.CARD_CREATED, NotificationOutcome.ALREADY_HANDLED)
    }

    /**
     * 이번 실행의 결과를 지표와 로그에 남긴다. 대상이 없어 일찍 끝난 실행은 이미 자기 로그를
     * 남겼으므로 건너뛴다.
     *
     * 루프가 끊긴 실행도 여기까지는 온다. 그래서 요약 첫 줄에 완료/중단을 함께 적는다.
     * "완료"로 못박으면 중단된 배치가 정상 종료로 읽힌다.
     */
    private fun record(tally: RunTally) {
        if (tally.targetCount == 0) {
            return
        }
        // 결과 종류가 늘어도 집계가 어긋나지 않도록 enum을 그대로 훑는다.
        AutoCardOutcome.entries.forEach { outcome ->
            tally.counts[outcome]?.let { outcomeCounters.getValue(outcome).increment(it.toDouble()) }
        }
        log.info(
            "자동 카드 생성 배치 {}: 대상={}, {}",
            if (tally.completed) "완료" else "중단",
            tally.targetCount,
            AutoCardOutcome.entries.joinToString(", ") { "${it.label}=${tally.counts[it] ?: 0}" },
        )
        // 시도한 회원 수만 남기면 '알림을 끈 사람들' 과 '정상 발송' 이 구분되지 않는다.
        // 04:30 리마인더와 같은 형식으로 실제 건수까지 남긴다.
        log.info(
            "카드 생성 알림: 대상={}명, 성공={}건, 실패={}건",
            tally.notifiedMemberIds.size,
            tally.notifiedSuccessCount,
            tally.notifiedFailureCount,
        )
    }

    /**
     * 배치 1회분의 집계. 루프가 끊겨도 살아남아야 하는 값들이라 한 곳에 모은다. 낱개 지역변수로
     * 흩어 두면 다음에 누가 집계 한 줄을 추가할 때 try 안쪽에 두기 쉽다.
     */
    private class RunTally {
        val counts = mutableMapOf<AutoCardOutcome, Int>()
        val notifiedMemberIds = mutableSetOf<Long>()
        var notifiedSuccessCount = 0
        var notifiedFailureCount = 0
        var targetCount = 0
        var completed = false
    }

    /**
     * 방 하나를 처리한 결과. [memberId] 는 카드를 실제로 만들었거나([AutoCardOutcome.CREATED]), 이미
     * 다른 경로로 만들어져 있던 경우([AutoCardOutcome.ALREADY_HANDLED])에만 채워진다 - 둘 다 알리거나
     * 기록을 남길 대상이 있다는 뜻이다.
     */
    private data class ProcessResult(
        val outcome: AutoCardOutcome,
        val memberId: Long? = null,
    )

    private fun process(conversationId: Long): ProcessResult =
        try {
            createCardForEndedConversation(conversationId)
        } catch (e: Exception) {
            // 방 하나의 예상 못 한 실패가 남은 방들을 막지 않게 한다. 카드 생성 상태는 실패 경로에서
            // 이미 FAILED로 되돌아가 있어 다음 실행이 다시 시도한다.
            log.error("자동 카드 생성 실패: conversationId={}", conversationId, e)
            ProcessResult(AutoCardOutcome.FAILED)
        }

    private fun createCardForEndedConversation(conversationId: Long): ProcessResult {
        val conversation =
            conversationService.endForAutoBatch(conversationId) ?: return ProcessResult(AutoCardOutcome.SKIPPED_DELETED)
        // 탈퇴는 회원 행만 익명화하고 대화방은 남긴다. 종료까지는 상태 정리라 무해하지만, 카드는
        // 탈퇴한 사람의 대화로 만드는 새 개인 데이터라 여기서 멈춘다.
        if (memberService.getById(conversation.memberId).isWithdrawn()) {
            return ProcessResult(AutoCardOutcome.WITHDRAWN_MEMBER)
        }
        return try {
            // 요약이 null이어도 그대로 넘긴다. 카드 생성 경로가 유저 메시지 원문으로 대신 만든다.
            cardService.createCard(conversation.memberId, conversationId, emotion = null, summary = conversation.summary)
            ProcessResult(AutoCardOutcome.CREATED, memberId = conversation.memberId)
        } catch (e: BusinessException) {
            // memberId 를 여기서 채워야 ALREADY_HANDLED 를 그 방·회원 이름으로 기록할 수 있다
            // (classify 는 outcome 만 가른다).
            ProcessResult(classify(e, conversationId), memberId = conversation.memberId)
        }
    }

    /** 배치 입장에서 정상인 실패와 진짜 실패를 가른다. */
    private fun classify(
        e: BusinessException,
        conversationId: Long,
    ): AutoCardOutcome =
        when (e.errorCode) {
            // 다른 요청이 먼저 카드를 만들었거나 만드는 중이다. 배치가 할 일이 없다.
            ErrorCode.CARD_ALREADY_EXISTS, ErrorCode.CARD_GENERATION_IN_PROGRESS -> {
                AutoCardOutcome.ALREADY_HANDLED
            }

            else -> {
                log.warn("자동 카드 생성 실패: conversationId={}, errorCode={}", conversationId, e.errorCode, e)
                AutoCardOutcome.FAILED
            }
        }

    companion object {
        /**
         * 하루 경계([AutoCardWindow.DAY_BOUNDARY_HOUR])에 맞춰 돈다. 하루가 끝나는 순간 그 하루를 정리한다.
         * 사용자 활동이 가장 적은 시간대라 LLM 호출이 몰려도 서비스 영향이 작다.
         *
         * 토큰 리셋도 같은 시각이라 배치가 쓰는 토큰은 **방금 리셋된 오늘 예산**에서 빠진다.
         * 리셋 직전(04시 등)으로 옮기면 곧 만료될 어제 예산에 잡혀 오늘 예산을 안 건드리지만,
         * 어제 상한을 다 쓴 사용자는 스킵돼 카드를 아예 못 받는다. 카드 1장은 감정 분류와 한 줄 생성
         * 2회로 하루 상한 대비 미미하므로 카드를 확실히 만드는 쪽을 택했다.
         *
         * `reset_hour`는 백오피스에서 바꿀 수 있는 값이다. 하루 경계를 옮기게 되면 이 상수와
         * [AutoCardWindow.DAY_BOUNDARY_HOUR]도 함께 봐야 한다.
         */
        private const val CRON = "0 0 ${AutoCardWindow.DAY_BOUNDARY_HOUR} * * *"
    }
}
