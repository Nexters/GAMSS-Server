package com.nexters.gamss.auth.repository

import com.nexters.gamss.auth.domain.SocialAccount
import org.springframework.data.jpa.repository.JpaRepository

interface SocialAccountRepository : JpaRepository<SocialAccount, Long> {
    fun findByProviderAndProviderId(
        provider: String,
        providerId: String,
    ): SocialAccount?
}
