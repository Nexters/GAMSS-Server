package com.nexters.gamss.member.repository

import com.nexters.gamss.member.domain.MemberSocialIdentity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional

interface MemberSocialIdentityRepository : JpaRepository<MemberSocialIdentity, Long> {
    fun existsByMemberId(memberId: Long): Boolean

    /**
     * 탈퇴 시 회원과 주체의 연결을 끊는다([com.nexters.gamss.member.service.MemberSocialIdentityCleaner]).
     *
     * 없는 행을 지워도 no-op 이다. 매핑 생성은 실패를 삼키는 부수 기록이라
     * ([com.nexters.gamss.member.service.MemberSocialIdentityService.ensureMapped]) 매핑 없는 회원이
     * 존재할 수 있고, 그 회원의 탈퇴가 여기서 깨지면 안 된다.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    fun deleteByMemberId(memberId: Long)
}
