package com.nexters.gamss.member.repository

import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.OAuthProvider
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByProviderAndProviderId(
        provider: OAuthProvider,
        providerId: String,
    ): Member?
}
