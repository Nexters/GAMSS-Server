package com.nexters.gamss.notification.service

import com.nexters.gamss.member.service.WithdrawnMemberCleaner
import com.nexters.gamss.notification.repository.DeviceTokenRepository
import org.springframework.stereotype.Component

/**
 * 회원 탈퇴 시 **등록된 기기**를 정리한다(notification 패키지 몫).
 *
 * 남겨두면 탈퇴한 사람의 기기로 알림이 계속 나간다. 게다가 같은 소셜 계정으로 재가입하면 새 회원
 * 번호를 받는데, 앱이 그 기기의 토큰을 다시 등록하기 전까지 옛 회원 번호에 묶인 행이 남아 있어
 * 발송 대상 조회가 지워진 사람을 가리킨다.
 *
 * [WithdrawnMemberCleaner] 를 구현해 붙는 형태라 member 패키지는 기기 토큰의 존재를 모른다 —
 * 정리 대상이 늘어도 탈퇴 코드는 그대로다(OCP).
 */
@Component
class DeviceTokenCleaner(
    private val deviceTokenRepository: DeviceTokenRepository,
) : WithdrawnMemberCleaner {
    override fun clean(memberId: Long) {
        deviceTokenRepository.deleteByMemberId(memberId)
    }
}
