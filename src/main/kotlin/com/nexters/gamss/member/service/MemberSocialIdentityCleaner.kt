package com.nexters.gamss.member.service

import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import org.springframework.stereotype.Component

/**
 * 탈퇴 시 회원과 쿼터 주체의 연결을 끊는다.
 *
 * **지워도 재가입 한도 이월은 깨지지 않는다.** 주체 키는 소셜 신원에서 결정론적으로 나오므로
 * ([SubjectKeyGenerator]), 같은 계정으로 다시 로그인하면 `ensureMapped` 가 **같은 주체 키로** 매핑을
 * 새로 만들고 기존 쿼터 행([com.nexters.gamss.tokenlimit.domain.TokenQuotaUsage])에 그대로 붙는다.
 * 이월을 떠받치는 것은 이 매핑이 아니라 주체 키의 결정론성이다.
 *
 * 탈퇴한 회원에게 이 행은 쓸 데가 없다. 다시 로그인할 수 없으니 적립도 판정도 일어나지 않는다.
 * 남겨두면 **탈퇴한 사람을 과거 행적에 다시 연결할 수 있는 고리**만 남으므로, 다른 자원과 같은
 * 시점에 함께 끊는다([com.nexters.gamss.auth.service.AuthResourceCleaner] 와 같은 계약).
 */
@Component
class MemberSocialIdentityCleaner(
    private val repository: MemberSocialIdentityRepository,
) : WithdrawnMemberCleaner {
    override fun clean(memberId: Long) {
        repository.deleteByMemberId(memberId)
    }
}
