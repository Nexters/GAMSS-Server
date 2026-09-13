package com.nexters.gamss.member.service

import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import org.springframework.stereotype.Component

/**
 * 탈퇴 시 회원과 쿼터 주체의 연결을 끊는다.
 *
 * 지워도 재가입 한도 이월은 깨지지 않는다 - 주체 키가 소셜 신원에서 결정론적으로 나오므로
 * ([SubjectKeyGenerator]) 재가입이 같은 키로 다시 잇는다. 탈퇴한 회원에게 이 행은 쓸 데가 없고,
 * 남겨두면 그 사람을 과거 행적에 다시 연결할 고리만 남는다.
 */
@Component
class MemberSocialIdentityCleaner(
    private val repository: MemberSocialIdentityRepository,
) : WithdrawnMemberCleaner {
    override fun clean(memberId: Long) {
        repository.deleteByMemberId(memberId)
    }
}
