package com.nexters.gamss.auth.repository

import com.nexters.gamss.auth.domain.SocialAccount
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SocialAccountRepositoryTest : RepositoryTest() {
    @Autowired
    private lateinit var socialAccountRepository: SocialAccountRepository

    @Test
    fun `provider와 providerId로 소셜 계정을 조회한다`() {
        socialAccountRepository.save(SocialAccount(memberId = 1L, provider = "GOOGLE", providerId = "sub-1"))

        val found = socialAccountRepository.findByProviderAndProviderId("GOOGLE", "sub-1")

        assertNotNull(found)
        assertEquals(1L, found.memberId)
    }

    @Test
    fun `일치하는 소셜 계정이 없으면 null을 반환한다`() {
        assertNull(socialAccountRepository.findByProviderAndProviderId("APPLE", "none"))
    }
}
