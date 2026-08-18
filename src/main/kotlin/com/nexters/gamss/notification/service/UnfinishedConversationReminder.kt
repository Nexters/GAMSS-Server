package com.nexters.gamss.notification.service

import com.nexters.gamss.card.service.AutoCardWindow
import com.nexters.gamss.conversation.repository.ConversationRepository
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
 * 경계를 본다([AutoCardWindow.createdBeforeOfNextRun]) — 지난 경계를 쓰면 어젯밤에 쓰다 만 방이 전부
 * 빠져서, 정작 30분 뒤에 닫히는 사람들이 알림을 못 받는다.
 *
 * **트랜잭션을 열지 않는다.** 발송은 외부 호출이라 트랜잭션 안에서 돌면 FCM 왕복 내내 DB 커넥션을
 * 쥔다([MemberPushNotifier] 가 그 상태를 거부한다).
 */
@Component
class UnfinishedConversationReminder(
    private val conversationRepository: ConversationRepository,
    private val window: AutoCardWindow,
    private val notifier: MemberPushNotifier,
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
     * 스케줄 진입점과 분리해 둔 것은 테스트가 기준 시각을 직접 주기 위해서다([com.nexters.gamss.card.service.DailyAutoCardScheduler]
     * 와 같은 이유).
     */
    fun runFor(
        createdAfter: Instant,
        createdBefore: Instant,
    ) {
        val memberIds = conversationRepository.findMemberIdsWithUnfinishedConversations(createdAfter, createdBefore)
        if (memberIds.isEmpty()) {
            log.info("미종료 대화방 리마인더: 대상 없음 (기준={}~{})", createdAfter, createdBefore)
            return
        }
        val result = notifier.send(memberIds, MESSAGE)
        log.info(
            "미종료 대화방 리마인더 완료: 대상={}명, 성공={}건, 실패={}건",
            memberIds.size,
            result.successCount,
            result.failureCount,
        )
    }

    companion object {
        /** 5시 배치([AutoCardWindow.DAY_BOUNDARY_HOUR])보다 30분 앞선다. */
        private const val CRON = "0 30 4 * * *"

        /**
         * 알림 문구. 이 알림이 알려야 하는 것은 **곧 자동으로 종료된다**는 예고까지다 — 카드가
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
