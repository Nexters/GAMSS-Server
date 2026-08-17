package com.nexters.gamss.notification.service

import com.nexters.gamss.notification.push.PushMessage
import com.nexters.gamss.notification.push.PushSendResult
import com.nexters.gamss.notification.push.PushSender
import com.nexters.gamss.notification.repository.DeviceTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * "이 회원들에게 이 알림을 보낸다"를 한 번의 호출로 만든다. 알림을 거는 쪽(배치·이벤트 리스너)이
 * 정할 것은 **누구에게 무슨 문구**뿐이고, 토큰을 모으고 죽은 토큰을 치우는 일은 여기서 끝낸다.
 *
 * 이 자리가 없으면 알림 종류마다 같은 세 단계(조회 → 발송 → 정리)가 복사된다.
 *
 * **트랜잭션 안에서 부를 수 없다.** 발송은 외부 호출이라, 트랜잭션 안에서 돌면 FCM 왕복이 끝날
 * 때까지 DB 커넥션을 쥐고 있게 된다. 조회와 삭제는 각자의 짧은 트랜잭션에서 돌고 그 사이의 발송은
 * 트랜잭션 밖이어야 한다.
 *
 * 여기에 `@Transactional` 을 **안 붙이는 것만으로는 부족하다** — 트랜잭션은 스레드로 전파되므로,
 * 부르는 쪽(예: `@Scheduled` 메서드)에 `@Transactional` 이 붙어 있으면 이 코드는 그 트랜잭션 안에서
 * 돈다. 결과는 정상이라 테스트로도 드러나지 않는다. 그래서 [send] 가 시작할 때 직접 확인하고
 * 거부한다 — 실수한 쪽이 그 자리에서 알게 하려는 것이다.
 *
 * 탈퇴 회원은 따로 거르지 않는다. 탈퇴하면 [com.nexters.gamss.notification.service.DeviceTokenCleaner]
 * 가 기기를 지우므로 애초에 보낼 토큰이 없다 — 여기서 한 번 더 확인하면 회원 조회가 늘기만 하고,
 * 대상 선정은 알림을 거는 쪽의 책임이다.
 */
@Component
class MemberPushNotifier(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushSender: PushSender,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * [memberIds] 의 모든 기기로 [message] 를 보내고, 죽은 것으로 판명된 토큰을 지운다.
     *
     * 등록된 기기가 없는 회원은 조용히 빠진다 — 알림을 끈 사용자이므로 실패가 아니다.
     */
    fun send(
        memberIds: Collection<Long>,
        message: PushMessage,
    ): PushSendResult {
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "푸시 발송은 트랜잭션 안에서 부를 수 없다 — 외부 호출이 끝날 때까지 DB 커넥션을 쥐게 된다."
        }
        val tokens = tokensOf(memberIds)
        if (tokens.isEmpty()) {
            return PushSendResult.none()
        }
        val result = pushSender.send(tokens, message)
        removeInvalid(result.invalidTokens)
        return result
    }

    private fun tokensOf(memberIds: Collection<Long>): List<String> =
        memberIds
            .distinct()
            .chunked(QUERY_CHUNK_SIZE)
            .flatMap { deviceTokenRepository.findTokenValuesByMemberIdIn(it) }

    /**
     * 발송이 끝난 뒤라 여기서 실패해도 발송 결과까지 잃을 이유는 없다. 다음 발송이 같은 토큰을
     * 다시 무효로 보고하므로 정리는 어차피 재시도된다 — 대신 조용히 넘어가지는 않게 남긴다.
     */
    private fun removeInvalid(invalidTokens: List<String>) {
        if (invalidTokens.isEmpty()) {
            return
        }
        // 청크마다 따로 삼킨다. 바깥에서 한 번에 감싸면 중간 청크가 터졌을 때 뒤쪽 청크는 시도조차
        // 못 하고, 앞 청크는 자기 트랜잭션으로 이미 커밋된 상태라 로그의 건수도 사실과 어긋난다.
        val removed = invalidTokens.chunked(QUERY_CHUNK_SIZE).sumOf { removeChunk(it) }
        log.info("죽은 디바이스 토큰 정리: 무효={}건, 삭제={}건", invalidTokens.size, removed)
    }

    private fun removeChunk(tokens: List<String>): Int =
        try {
            deviceTokenRepository.deleteByTokenValueIn(tokens)
        } catch (e: Exception) {
            log.error("죽은 디바이스 토큰 정리 실패: {}건", tokens.size, e)
            0
        }

    companion object {
        /**
         * `IN` 절에 한 번에 넣는 개수. 04:30 리마인더는 대상이 전체 회원이 될 수 있어, 목록을
         * 그대로 넘기면 파라미터가 수천 개인 쿼리가 만들어진다.
         */
        private const val QUERY_CHUNK_SIZE = 500
    }
}
