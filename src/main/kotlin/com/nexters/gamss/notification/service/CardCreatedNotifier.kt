package com.nexters.gamss.notification.service

import com.nexters.gamss.notification.push.PushMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 카드가 만들어진 회원에게 "카드가 도착했다"고 알린다.
 *
 * 04:30 리마인더([UnfinishedConversationReminder])와 달리 대상을 조회하지 않는다. 그쪽은 "지금 이
 * 조건인 사람이 누구인가"를 물어야 해서 조회가 본질이지만, 이쪽은 **방금 카드를 만들어준 그 사람**이라
 * 부르는 쪽이 이미 답을 들고 있다.
 *
 * 조회로 뒤늦게 훑는 방식도 만들어 봤지만 접었다 — 배치가 언제 끝날지 가정해야 하고, 그 시간을 넘겨
 * 끝난 카드는 알림에서 조용히 빠진다.
 */
@Component
class CardCreatedNotifier(
    private val notifier: MemberPushNotifier,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * [memberId] 에게 카드 도착을 알린다.
     *
     * **예외를 밖으로 내보내지 않는다.** 부르는 쪽은 카드 생성 배치이고, 알림이 실패했다고 해서 이미
     * 만들어진 카드가 실패로 집계되면 안 된다 — 배치는 방 하나의 예외를 그 방의 실패로 처리한다.
     */
    fun notifyCardCreated(memberId: Long) {
        try {
            notifier.send(listOf(memberId), MESSAGE)
        } catch (e: Exception) {
            log.error("카드 생성 알림 실패: memberId={}", memberId, e)
        }
    }

    companion object {
        /**
         * 알림 문구. 두 가지를 담지 않는다.
         *
         * - **카드 내용**: 잠금화면에 감정 기록이 그대로 뜨는 것은 이 서비스에서 특히 부담이 크다.
         *   무슨 카드인지는 앱에서 확인하게 한다(04:30 알림이 종료 예고까지만 하는 것과 같은 규칙).
         * - **장수**: 한 사람이 방을 여러 개 만들면 카드도 여러 장 나오는데, 알림은 첫 카드에 한 번만
         *   보내므로 그 시점에 몇 장이 될지 모른다. "한 장"이라고 쓰면 틀린 말이 된다.
         */
        private val MESSAGE =
            PushMessage(
                title = "오늘의 감정 카드가 도착했어요",
                body = "어제 하루가 카드로 정리됐어요. 지금 확인해보세요.",
            )
    }
}
