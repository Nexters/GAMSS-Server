package com.nexters.gamss.card.service

import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.member.service.WithdrawnMemberCleaner
import org.springframework.stereotype.Component

/**
 * 회원 탈퇴 시 **공유 링크**를 회수한다(card 패키지 몫).
 *
 * 공유 조회는 인증 없이 열리는 유일한 카드 경로라, 탈퇴해도 링크를 받은 사람은 그대로 열 수 있다.
 * 인증이 필요한 경로는 accessToken 수명이 지나면 닫히지만 이쪽은 스스로 닫히는 시점이 없다 —
 * 탈퇴한 사람의 감정 기록이 회수할 수 없는 주소로 계속 열려 있게 된다.
 *
 * 카드는 지우지 않고 토큰만 NULL 로 되돌린다. 탈퇴는 원래 카드를 건드리지 않았고
 * ([com.nexters.gamss.member.service.MemberService.withdraw]), 여기서 끊어야 하는 것은
 * 인증 없이 열리는 경로뿐이다.
 *
 * [WithdrawnMemberCleaner] 를 구현해 붙는 형태라 member 패키지는 공유 링크의 존재를 모른다 —
 * 정리 대상이 늘어도 탈퇴 코드는 그대로다(OCP). 채팅방 삭제 쪽의
 * [DeletedConversationCardCleaner] 와 같은 모양이다.
 */
@Component
class WithdrawnMemberShareLinkCleaner(
    private val cardRepository: CardRepository,
) : WithdrawnMemberCleaner {
    override fun clean(memberId: Long) {
        cardRepository.revokeShareTokensByMemberId(memberId)
    }
}
