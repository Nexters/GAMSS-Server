package com.nexters.gamss.notification.service

import com.nexters.gamss.card.service.AutoCardWindow
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.notification.push.PushMessage
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 새벽 배치가 카드를 만들어준 회원에게 알린다. 04:30 리마인더([UnfinishedConversationReminder])와
 * 같은 모양이다 — 조회하고, 한 번에 보내고, 결과를 남긴다.
 *
 * 카드는 자고 있는 사이에 만들어져서, 앱을 열어보기 전에는 생겼다는 것 자체를 모른다. 04:30 알림을
 * 받고 그냥 잔 사람에게는 더더욱 결과를 알릴 방법이 없다.
 *
 * **배치가 끝난 뒤에 돈다.** 05:00 배치는 방마다 LLM 을 두 번 부르므로 대상이 많으면 몇 분씩 걸린다.
 * 같은 시각에 돌면 아직 만들어지지 않은 카드를 두고 조회하게 되므로 [DELAY_MINUTES] 만큼 미룬다.
 *
 * **트랜잭션을 열지 않는다.** 발송은 외부 호출이라 트랜잭션 안에서 돌면 FCM 왕복 내내 DB 커넥션을
 * 쥔다([MemberPushNotifier] 가 그 상태를 거부한다).
 */
@Component
class CardCreatedNotifier(
    private val conversationRepository: ConversationRepository,
    private val window: AutoCardWindow,
    private val notifier: MemberPushNotifier,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = CRON, zone = AutoCardWindow.ZONE_ID)
    fun notifyCardCreated() {
        // 이번 실행의 배치가 시작한 경계. 05:30 에는 오늘 05:00 이므로, 그 뒤에 DONE 이 된 것들이
        // 곧 방금 배치가 만든 카드다.
        runSince(window.createdBefore())
    }

    /**
     * [since] 이후에 카드 생성이 끝난 회원들에게 알린다. 스케줄 진입점과 분리해 둔 것은 테스트가
     * 기준 시각을 직접 주기 위해서다.
     */
    fun runSince(since: Instant) {
        val memberIds = conversationRepository.findMemberIdsWithCardCreatedSince(since)
        if (memberIds.isEmpty()) {
            log.info("카드 생성 알림: 대상 없음 (기준={} 이후)", since)
            return
        }
        val result = notifier.send(memberIds, MESSAGE)
        log.info(
            "카드 생성 알림 완료: 대상={}명, 성공={}건, 실패={}건",
            memberIds.size,
            result.successCount,
            result.failureCount,
        )
    }

    companion object {
        /**
         * 배치가 끝나기를 기다리는 시간. 지금 규모에서는 배치가 수십 초면 끝나지만, 대상이 늘면
         * 방마다 LLM 두 번씩이라 길어진다. 이 시간을 넘겨 끝난 카드는 이번 알림에서 빠지고 다시
         * 시도되지 않는다 — 발송 이력(#155)이 붙으면 남은 대상을 다음 실행이 집을 수 있다.
         */
        private const val DELAY_MINUTES = 30

        private const val CRON = "0 $DELAY_MINUTES 5 * * *"

        /**
         * 알림 문구. 카드 한 줄은 담지 않는다 — 잠금화면에 감정 기록이 그대로 뜨는 것은 이 서비스에서
         * 특히 부담이 크다. 무슨 카드인지는 앱에서 확인하게 한다.
         */
        private val MESSAGE =
            PushMessage(
                title = "오늘의 감정 카드가 도착했어요",
                body = "어제 하루가 카드 한 장으로 정리됐어요. 지금 확인해보세요.",
            )
    }
}
