package com.nexters.gamss.notification.service

import com.nexters.gamss.notification.push.PushMessage
import com.nexters.gamss.notification.push.PushSendResult
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
     * [memberId] 에게 카드 도착을 알린다. **발송 결과를 돌려준다** — 부르는 쪽이 몇 건이 실제로
     * 나갔는지 남길 수 있어야 한다. 알림을 끈 회원(등록된 기기 없음)에게는 아무것도 나가지 않는데,
     * 시도한 회원 수만 세면 그 상태와 정상 발송이 로그에서 구분되지 않는다.
     *
     * **발송 실패를 밖으로 내보내지 않는다.** 부르는 쪽은 카드 생성 배치이고, 알림이 실패했다고 해서
     * 이미 만들어진 카드가 실패로 집계되면 안 된다 — 배치는 방 하나의 예외를 그 방의 실패로 처리한다.
     *
     * 다만 [PushInTransactionException] 은 그대로 올려보낸다. [MemberPushNotifier] 의 "트랜잭션 안에서
     * 부르지 마라"는 가드인데, 그것까지 삼키면 **가드를 넣은 이유가 사라진다** —
     * 배치를 트랜잭션으로 감싸는 실수가 로그 한 줄로 묻히고 배치는 초록불로 끝난다. 이 타입은 발송
     * 실패가 아니라 코드가 잘못됐다는 신호다.
     *
     * **대가를 알고 쓴다: 이 예외는 배치 루프를 중단시킨다.** 부르는 쪽에 catch 가 없어서 남은 방들은
     * 그날 종료도 카드 생성도 되지 않는다(3개 중 1개만 처리되는 것을 실제로 확인했다). 그래도 계속
     * 도는 것보다 낫다고 봤다 — 가드가 발동했다는 것은 배치가 트랜잭션 안에 있다는 뜻이고, 그 상태로
     * 끝까지 돌면 DB 커넥션을 붙잡은 채 LLM 을 수백 번 부른다.
     */
    fun notifyCardCreated(memberId: Long): PushSendResult =
        try {
            notifier.send(listOf(memberId), MESSAGE)
        } catch (e: PushInTransactionException) {
            throw e
        } catch (e: Exception) {
            log.error("카드 생성 알림 실패: memberId={}", memberId, e)
            // none() 이 아니라 failed() 다. none() 은 '알림을 끈 회원'이라는 정상 상태인데, 여기로
            // 오는 것은 토큰 조회·정리가 끊긴 경우라 발송 여부조차 알 수 없다. 둘을 같은 값으로
            // 돌려주면 백오피스에서 장애가 '기기 없음'으로 보인다.
            PushSendResult.failed()
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
