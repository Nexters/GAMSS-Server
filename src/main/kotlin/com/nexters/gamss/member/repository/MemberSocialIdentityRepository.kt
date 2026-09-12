package com.nexters.gamss.member.repository

import com.nexters.gamss.member.domain.MemberSocialIdentity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional

interface MemberSocialIdentityRepository : JpaRepository<MemberSocialIdentity, Long> {
    fun existsByMemberId(memberId: Long): Boolean

    /** 없는 행을 지워도 no-op 이다. 매핑 생성이 실패를 삼키는 부수 기록이라 없는 회원도 있다. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    fun deleteByMemberId(memberId: Long)
}
