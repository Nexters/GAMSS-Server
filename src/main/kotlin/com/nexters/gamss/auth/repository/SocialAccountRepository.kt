package com.nexters.gamss.auth.repository

import com.nexters.gamss.auth.domain.SocialAccount
import org.springframework.data.jpa.repository.JpaRepository

interface SocialAccountRepository : JpaRepository<SocialAccount, Long> {
    fun findByProviderAndProviderId(
        provider: String,
        providerId: String,
    ): SocialAccount?

    /** 탈퇴 시 연결을 끊어, 같은 소셜 계정으로 신규 회원으로 재가입할 수 있게 한다. */
    fun deleteByMemberId(memberId: Long)
}
