package com.nexters.gamss.notification.service

import com.nexters.gamss.notification.domain.NotificationLog
import com.nexters.gamss.notification.domain.NotificationOutcome
import com.nexters.gamss.notification.domain.NotificationType
import com.nexters.gamss.notification.push.PushSendResult
import com.nexters.gamss.notification.repository.NotificationLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 알림 발송 결과를 남긴다. 기록만 하고 발송은 하지 않는다.
 *
 * **기록 실패가 발송을 실패로 만들지 않는다.** 이 기록은 관측용이라, 알림은 이미 사용자에게 나간
 * 뒤다. 여기서 예외가 올라가면 배치가 그 방을 실패로 집계하고 다음 회차에 다시 시도하는데, 그러면
 * 같은 사람에게 알림이 두 번 간다([com.nexters.gamss.monitoring.service.GenerationLogRecorder] 와
 * 같은 계약).
 *
 * **트랜잭션을 따로 열지 않는다.** 부르는 자리는 푸시를 보낸 직후인데 그 자리는 트랜잭션이 없음이
 * 보장된다([MemberPushNotifier.send] 가 트랜잭션 안에서 불리면 예외를 던진다). 그래서 리포지토리
 * 저장이 자기 트랜잭션에서 돌고 끝난다.
 */
@Service
class NotificationLogRecorder(
    private val notificationLogRepository: NotificationLogRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** [conversationIds] 각각에 대해 [result] 가 말하는 결과를 남긴다. */
    fun record(
        memberId: Long,
        conversationIds: Collection<Long>,
        type: NotificationType,
        result: PushSendResult,
    ) = record(memberId, conversationIds, type, outcomeOf(result))

    /** 보내지 않기로 한 경우까지 포함해 [outcome] 을 그대로 남긴다. */
    fun record(
        memberId: Long,
        conversationIds: Collection<Long>,
        type: NotificationType,
        outcome: NotificationOutcome,
    ) {
        if (conversationIds.isEmpty()) {
            return
        }
        runCatching {
            notificationLogRepository.saveAll(
                conversationIds.distinct().map { NotificationLog(memberId, it, type, outcome) },
            )
        }.onFailure {
            log.error("알림 발송 기록 실패(무시): memberId={}, type={}, outcome={}", memberId, type, outcome, it)
        }
    }

    /**
     * 발송 결과를 결과값으로 옮긴다.
     *
     * 성공이 하나라도 있으면 SENT 다. 한 회원이 기기를 여럿 등록해 두면 일부만 실패할 수 있는데,
     * 그 사람은 알림을 받았으므로 실패로 적으면 사실과 다르다.
     *
     * 성공도 실패도 0이면 보낼 토큰이 없었다는 뜻이다([MemberPushNotifier.send] 가 그때
     * [PushSendResult.none] 을 돌려준다).
     */
    private fun outcomeOf(result: PushSendResult): NotificationOutcome =
        when {
            result.successCount > 0 -> NotificationOutcome.SENT
            result.failureCount > 0 -> NotificationOutcome.FAILED
            else -> NotificationOutcome.NO_DEVICE
        }
}
