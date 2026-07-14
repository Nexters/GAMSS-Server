package com.nexters.gamss.member.repository

import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.OAuthProvider
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MemberRepositoryTest : RepositoryTest() {
    @Autowired
    lateinit var memberRepository: MemberRepository

    @Test
    fun `provider와 providerId로 회원을 조회한다`() {
        memberRepository.save(Member(OAuthProvider.GOOGLE, "sub-100", "a@example.com"))

        val found = memberRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "sub-100")

        assertNotNull(found)
        assertEquals("a@example.com", found.email)
    }

    @Test
    fun `없는 회원이면 null을 반환한다`() {
        val found = memberRepository.findByProviderAndProviderId(OAuthProvider.APPLE, "not-exist")

        assertNull(found)
    }
}
