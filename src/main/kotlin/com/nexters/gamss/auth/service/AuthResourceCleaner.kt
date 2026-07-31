package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.repository.RefreshTokenRepository
import com.nexters.gamss.auth.repository.SocialAccountRepository
import com.nexters.gamss.member.service.WithdrawnMemberCleaner
import org.springframework.stereotype.Component

/**
 * 회원 탈퇴 시 **인증 자원**을 정리한다(auth 패키지 몫).
 *
 * - **소셜 계정**: `(provider, providerId)` 유니크 제약 때문에 남겨두면, 같은 소셜 계정으로 다시
 *   로그인해도 탈퇴한 회원이 그대로 조회돼 재가입이 영영 막힌다. `providerId`(소셜 고유 식별자)
 *   자체가 개인 식별정보라 익명화 관점에서도 지우는 게 맞다.
 * - **리프레시 토큰**: 남겨두면 탈퇴 뒤에도 잔여 유효기간(최대 14일) 동안 재발급이 시도된다.
 *   (`LoginService.reissue`가 탈퇴 회원을 막고는 있지만, 파기해야 할 자격증명을 남길 이유가 없다.)
 */
@Component
class AuthResourceCleaner(
    private val socialAccountRepository: SocialAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) : WithdrawnMemberCleaner {
    override fun clean(memberId: Long) {
        socialAccountRepository.deleteByMemberId(memberId)
        refreshTokenRepository.deleteByMemberId(memberId)
    }
}
