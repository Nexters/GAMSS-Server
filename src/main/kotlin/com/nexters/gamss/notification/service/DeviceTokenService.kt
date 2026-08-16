package com.nexters.gamss.notification.service

import com.nexters.gamss.notification.domain.FcmToken
import com.nexters.gamss.notification.repository.DeviceTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 푸시를 받을 기기 목록을 관리한다. 등록·해제만 맡고, 실제 발송은 알림 발송 쪽 몫이다.
 *
 * 두 연산 모두 **몇 번을 호출해도 결과가 같다**. 앱은 실행할 때마다 등록을 다시 부르고, 권한을
 * 껐다 켜면 해제와 등록이 섞여 들어온다 — 호출 횟수나 순서를 앱이 맞춰야 한다면 언젠가 어긋난다.
 */
@Service
class DeviceTokenService(
    private val deviceTokenRepository: DeviceTokenRepository,
) {
    /**
     * [token] 기기를 [memberId] 의 것으로 등록한다. 같은 기기를 다른 회원이 쓰기 시작했다면
     * 소유자를 옮긴다([DeviceTokenRepository.upsert]).
     */
    @Transactional
    fun register(
        memberId: Long,
        token: FcmToken,
    ) {
        deviceTokenRepository.upsert(memberId, token.value)
    }

    /**
     * 등록을 해제한다. 알림 권한을 껐거나 로그아웃했을 때 앱이 부른다 — 이 서비스에는 수신 동의
     * 플래그가 따로 없고 **행을 지우는 것이 곧 수신 거부**다.
     *
     * 이미 없는 토큰이어도 성공으로 둔다. 해제는 "이 기기로 보내지 마라"는 요청이고 그 결과는
     * 이미 충족돼 있다.
     */
    @Transactional
    fun unregister(
        memberId: Long,
        token: FcmToken,
    ) {
        deviceTokenRepository.deleteByMemberIdAndToken(memberId, token)
    }
}
