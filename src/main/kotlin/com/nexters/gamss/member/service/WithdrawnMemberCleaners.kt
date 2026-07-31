package com.nexters.gamss.member.service

import org.springframework.stereotype.Component

/**
 * 등록된 [WithdrawnMemberCleaner] 전체를 감싸는 일급 컬렉션.
 *
 * 정리 대상이 늘어도 구현체만 추가하면 되고 [MemberService]의 생성자·메서드는 그대로다 —
 * 목록을 raw List 로 넘기면 사용하는 쪽마다 순회 로직이 흩어지므로 여기에 가둔다.
 */
@Component
class WithdrawnMemberCleaners(
    private val cleaners: List<WithdrawnMemberCleaner>,
) {
    fun cleanAll(memberId: Long) {
        cleaners.forEach { it.clean(memberId) }
    }
}
