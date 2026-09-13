package com.nexters.gamss.member.repository

import com.nexters.gamss.member.domain.MemberSocialIdentity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface MemberSocialIdentityRepository : JpaRepository<MemberSocialIdentity, Long> {
    fun existsByMemberId(memberId: Long): Boolean

    /**
     * 없으면 만들고 있으면 그대로 둔다.
     *
     * upsert 한 문장인 것은 동시 최초 로그인 경합에서 유니크 위반이 **나지 않게** 하려는 것이다.
     * 예외로 다루면 부르는 쪽 트랜잭션이 rollback-only 로 표시돼 로그인 자체가 실패하고, 그것을
     * 피하려 별도 트랜잭션을 열면 로그인이 커넥션을 둘 쥐면서 매핑만 따로 커밋된다.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value =
            "insert into member_social_identity (member_id, subject_key, created_at) " +
                "values (:memberId, :subjectKey, now(6)) " +
                "on duplicate key update member_id = member_id",
        nativeQuery = true,
    )
    fun upsert(
        @Param("memberId") memberId: Long,
        @Param("subjectKey") subjectKey: String,
    )

    /** 없는 행을 지워도 no-op 이다. 매핑이 없는 회원도 있을 수 있다. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    fun deleteByMemberId(memberId: Long)
}
