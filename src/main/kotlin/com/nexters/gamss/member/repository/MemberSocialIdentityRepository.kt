package com.nexters.gamss.member.repository

import com.nexters.gamss.member.domain.MemberSocialIdentity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface MemberSocialIdentityRepository : JpaRepository<MemberSocialIdentity, Long> {
    fun existsByMemberId(memberId: Long): Boolean

    /**
     * 탈퇴한 회원의 매핑 중 [deletedBefore] 이전에 탈퇴한 것을 지운다.
     *
     * 활성 회원의 매핑은 건드리지 않는다 - 지우면 그 회원의 사용량이 더는 집계되지 않아 한도가
     * 사실상 꺼진다. 탈퇴한 회원만, 그것도 한 구간이 지난 뒤에 끊는 것이 이 기능이 감수하는 선이다
     * ([com.nexters.gamss.member.domain.MemberSocialIdentity]).
     *
     * `members.deleted_at` 을 기준으로 삼는다([com.nexters.gamss.member.domain.Member.withdraw] 가
     * 채우는 값) - 매핑에 탈퇴 시각을 복제해 두면 두 값이 어긋날 여지가 생긴다.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value =
            "delete i from member_social_identity i join members m on m.id = i.member_id " +
                "where m.status = 'WITHDRAWN' and m.deleted_at is not null and m.deleted_at < :deletedBefore",
        nativeQuery = true,
    )
    fun deleteWithdrawnBefore(
        @Param("deletedBefore") deletedBefore: Instant,
    ): Int
}
