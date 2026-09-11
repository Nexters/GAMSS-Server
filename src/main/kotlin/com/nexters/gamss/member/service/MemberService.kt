package com.nexters.gamss.member.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.InitialNicknameResolver
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.MemberStatus
import com.nexters.gamss.member.domain.Nickname
import com.nexters.gamss.member.repository.MemberRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 회원 유스케이스. 백오피스 집계는 [MemberStatsService] 가 맡는다. */
@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val withdrawnMemberCleaners: WithdrawnMemberCleaners,
    private val initialNicknameResolver: InitialNicknameResolver = InitialNicknameResolver(),
) {
    @Transactional(readOnly = true)
    fun getById(id: Long): Member =
        memberRepository
            .findById(id)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

    /** 백오피스 회원 목록. 빈 검색어는 전체 조회로, [status] null 은 모든 상태로 취급한다. */
    @Transactional(readOnly = true)
    fun search(
        keyword: String?,
        status: MemberStatus?,
        pageable: Pageable,
    ): Page<Member> = memberRepository.search(keyword?.takeIf { it.isNotBlank() }, status, pageable)

    // 닉네임 초기값은 [InitialNicknameResolver] 가 정한다 - 소셜 이름을 우선 쓰되, 규칙(길이·
    // 금칙어)에 어긋나면 단어 단위 분리·절단을 거쳐 마지막엔 무작위 닉네임으로 반드시 채운다.
    @Transactional
    fun create(
        email: String?,
        name: String?,
    ): Member = memberRepository.save(Member(email, name, initialNicknameResolver.resolve(name)))

    @Transactional
    fun updateNickname(
        id: Long,
        nickname: Nickname,
    ): Member {
        val member = getById(id)
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
        val member = getById(id)
        if (member.isWithdrawn()) {
            throw BusinessException(ErrorCode.ALREADY_WITHDRAWN)
        }
        member.withdraw()
        withdrawnMemberCleaners.cleanAll(id)
    }
}
