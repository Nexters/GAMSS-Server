package com.nexters.gamss.member.repository

import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.MemberStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MemberRepository : JpaRepository<Member, Long> {
    /**
     * 백오피스 회원 검색. keyword 가 null 이면 전체를, 있으면 이메일·닉네임 부분 일치로 조회한다.
     * status 가 null 이면 모든 상태를, 있으면 해당 상태만 조회한다.
     */
    @Query(
        """
        select m from Member m
        where (:keyword is null
               or lower(m.email) like lower(concat('%', :keyword, '%'))
               or lower(m.nickname.value) like lower(concat('%', :keyword, '%')))
          and (:status is null or m.status = :status)
        """,
    )
    fun search(
        @Param("keyword") keyword: String?,
        @Param("status") status: MemberStatus?,
        pageable: Pageable,
    ): Page<Member>
}
