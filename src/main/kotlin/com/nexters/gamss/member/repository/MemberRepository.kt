package com.nexters.gamss.member.repository

import com.nexters.gamss.member.domain.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long>
