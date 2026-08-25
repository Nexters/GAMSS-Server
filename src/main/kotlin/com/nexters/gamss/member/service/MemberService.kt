package com.nexters.gamss.member.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.Nickname
import com.nexters.gamss.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 회원을 만들고 바꾸고 탈퇴시킨다. 읽기는 [MemberReadService] 가 맡는다. */
@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val memberReadService: MemberReadService,
    private val withdrawnMemberCleaners: WithdrawnMemberCleaners,
) {
    // 닉네임 초기값은 소셜 이름. 이름이 닉네임 규칙(길이·금칙어)에 어긋나면 미설정(null)으로 둔다.
    @Transactional
    fun create(
        email: String?,
        name: String?,
    ): Member = memberRepository.save(Member(email, name, Nickname.tryCreate(name)))

    @Transactional
    fun updateNickname(
        id: Long,
        nickname: Nickname,
    ): Member {
        val member = memberReadService.getById(id)
        member.updateNickname(nickname)
        return member
    }

    /**
     * 탈퇴한다. 회원 도메인의 상태 전이·익명화만 여기서 하고, 탈퇴에 딸린 다른 자원
     * (소셜 계정·리프레시 토큰 등)의 정리는 [WithdrawnMemberCleaner] 구현들이 각자 맡는다.
     * member 패키지가 그 자원들을 알지 않게 하고(의존은 `구현 패키지 -> member` 단방향),
     * 정리 대상이 늘어도 이 메서드는 그대로 두기 위함이다.
     *
     * 같은 트랜잭션에서 동기로 호출하므로 탈퇴와 정리는 함께 커밋되거나 함께 롤백된다.
     */
    @Transactional
    fun withdraw(id: Long) {
        val member = memberReadService.getById(id)
        if (member.isWithdrawn()) {
            throw BusinessException(ErrorCode.ALREADY_WITHDRAWN)
        }
        member.withdraw()
        withdrawnMemberCleaners.cleanAll(id)
    }
}
