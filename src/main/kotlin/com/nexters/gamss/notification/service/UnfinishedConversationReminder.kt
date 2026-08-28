package com.nexters.gamss.notification.service

import com.nexters.gamss.card.service.AutoCardWindow
import com.nexters.gamss.conversation.service.ConversationService
import com.nexters.gamss.notification.domain.NotificationType
import com.nexters.gamss.notification.push.PushMessage
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 새벽 5시 배치가 방을 대신 닫기 **30분 전에**, 아직 미종료 상태인 방을 가진 회원에게 한 번 알린다.
 *
 * 배치는 종료 버튼을 누르지 않은 방을 대신 닫고 카드를 만든다. 사용자 입장에서는 모르는 사이에 방이
 * 닫히는 셈이라, 직접 마무리할 기회를 먼저 준다.
 *
 * 대상은 **30분 뒤 배치가 닫을 방**과 같아야 한다. 그래서 상한으로 지난 경계가 아니라 다음 배치가 쓸
 * 경계를 본다([AutoCardWindow.createdBeforeOfNextRun]). 지난 경계를 쓰면 어젯밤에 쓰다 만 방이 전부
 * 빠져서, 정작 30분 뒤에 닫히는 사람들이 알림을 못 받는다.
 *
 * **트랜잭션을 열지 않는다.** 발송은 외부 호출이라 트랜잭션 안에서 돌면 FCM 왕복 내내 DB 커넥션을
 * 쥔다([MemberPushNotifier] 가 그 상태를 거부한다).
 */
@Component
class UnfinishedConversationReminder(
    private val conversationService: ConversationService,
    private val window: AutoCardWindow,
    private val notifier: MemberPushNotifier,
    private val notificationLogRecorder: NotificationLogRecorder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = CRON, zone = AutoCardWindow.ZONE_ID)
    fun remindUnfinished() {
        runFor(
            createdAfter = window.createdAfter(),
            createdBefore = window.createdBeforeOfNextRun(),
        )
    }

    /**
     * [createdAfter] 와 [createdBefore] 사이에 만들어진 방 중 아직 미종료인 것들의 주인에게 알린다.
     *
     * 발송 실패를 삼키지 않는다. [CardCreatedNotifier] 와 다른 점이다. 그쪽은 배치 루프 한가운데서
     * 불려서 예외가 올라가면 멀쩡히 만들어진 카드가 실패로 집계되지만, 여기는 이 실행의 꼭대기라
     * 예외가 나가도 그 회차가 끝날 뿐이다. 감싸면 오히려 [MemberPushNotifier] 의 트랜잭션 가드까지
     * 삼켜 실수가 묻힌다.
     *
     * **대가를 알고 쓴다: 회원마다 따로 보내므로 중간에 터지면 부분 상태로 끝난다.** 앞선 회원들은
     * 알림을 받고 기록도 남지만 뒤는 통째로 빠진다. 전원에게 한 번에 보내던 때는 예외가 곧 '아무도
     * 못 받았다'였으니, 그때의 전제로 읽지 말 것. 한 회원의 일시적 오류로 나머지가 막히는 것이
     * 문제가 되면 회원마다 삼키고 계속 도는 쪽으로 바꿔야 한다.
     *
     * 스케줄 진입점과 분리해 둔 것은 테스트가 기준 시각을 직접 주기 위해서다
     * ([com.nexters.gamss.card.service.DailyAutoCardScheduler] 와 같은 이유).
     */
    fun runFor(
        createdAfter: Instant,
        createdBefore: Instant,
    ) {
        val targets = conversationService.findUnfinishedConversations(createdAfter, createdBefore)
        if (targets.isEmpty()) {
            log.info("미종료 대화방 리마인더: 대상 없음 (기준={}~{})", createdAfter, createdBefore)
            return
        }
        // 발송은 회원당 한 번이지만 기록은 방마다 남긴다. 백오피스가 "이 방 때문에 알림이 갔는가"를
        // 보여주려면 회원 단위 기록으로는 부족하다.
        val conversationIdsByMember = targets.groupBy({ it.memberId }, { it.conversationId })
        // **회원마다 따로 보낸다.** 한 번에 몰아 보내면 결과가 전원분 합계로만 돌아와, 누구는 받고
        // 누구는 기기가 없어도 전부 '발송'으로 기록된다. 대화방별 발송 결과라는 계약이 깨지므로
        // FCM 왕복이 늘어나는 것을 감수한다(새벽 배치이고 대상은 수십 명 단위다).
        var successCount = 0
        var failureCount = 0
        conversationIdsByMember.forEach { (memberId, conversationIds) ->
            val result = notifier.send(listOf(memberId), MESSAGE)
            notificationLogRecorder.record(memberId, conversationIds, NotificationType.UNFINISHED_REMINDER, result)
            successCount += result.successCount
            failureCount += result.failureCount
        }
        log.info(
            "미종료 대화방 리마인더 완료: 대상={}명, 대화방={}개, 성공={}건, 실패={}건",
            conversationIdsByMember.size,
            targets.size,
            successCount,
            failureCount,
        )
    }

    companion object {
        /** 5시 배치([AutoCardWindow.DAY_BOUNDARY_HOUR])보다 30분 앞선다. */
        private const val CRON = "0 30 4 * * *"

        /**
         * 알림 문구. 이 알림이 알려야 하는 것은 **곧 자동으로 종료된다**는 예고까지다. 카드가
         * 만들어졌다는 소식은 5시 발송이 따로 맡으므로(#154) 여기서 카드를 약속하면 두 알림이 겹친다.
         *
         * 잠금화면에 그대로 뜨는 값이라 대화 내용은 담지 않는다.
         */
        private val MESSAGE =
            PushMessage(
                title = "곧 오늘의 대화가 종료돼요",
                body = "아직 종료하지 않은 대화가 있어요. 직접 마무리하려면 지금 확인해주세요.",
            )
    }
}
