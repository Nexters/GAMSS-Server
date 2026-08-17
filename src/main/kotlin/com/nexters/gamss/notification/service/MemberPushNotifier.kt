package com.nexters.gamss.notification.service

import com.nexters.gamss.notification.push.PushMessage
import com.nexters.gamss.notification.push.PushSendResult
import com.nexters.gamss.notification.push.PushSender
import com.nexters.gamss.notification.repository.DeviceTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * "이 회원들에게 이 알림을 보낸다"를 한 번의 호출로 만든다. 알림을 거는 쪽(배치·이벤트 리스너)이
 * 정할 것은 **누구에게 무슨 문구**뿐이고, 토큰을 모으고 죽은 토큰을 치우는 일은 여기서 끝낸다.
 *
 * 이 자리가 없으면 알림 종류마다 같은 세 단계(조회 → 발송 → 정리)가 복사된다.
 *
 * **트랜잭션을 열지 않는다.** 발송은 외부 호출이라 응답이 늦으면 그만큼 커넥션을 쥐고 있게 된다.
 * 조회와 삭제는 각자의 짧은 트랜잭션에서 돌고, 그 사이의 발송은 트랜잭션 밖이다.
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
        try {
            val removed =
                invalidTokens
                    .chunked(QUERY_CHUNK_SIZE)
                    .sumOf { deviceTokenRepository.deleteByTokenValueIn(it) }
            log.info("죽은 디바이스 토큰 정리: 보고={}건, 삭제={}건", invalidTokens.size, removed)
        } catch (e: Exception) {
            log.error("죽은 디바이스 토큰 정리 실패: {}건", invalidTokens.size, e)
        }
    }

    companion object {
        /**
         * `IN` 절에 한 번에 넣는 개수. 04:30 리마인더는 대상이 전체 회원이 될 수 있어, 목록을
         * 그대로 넘기면 파라미터가 수천 개인 쿼리가 만들어진다.
         */
        private const val QUERY_CHUNK_SIZE = 500
    }
}
