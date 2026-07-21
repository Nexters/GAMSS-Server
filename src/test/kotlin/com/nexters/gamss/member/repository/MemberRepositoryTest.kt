package com.nexters.gamss.member.repository

import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.Nickname
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemberRepositoryTest : RepositoryTest() {
    @Autowired
    lateinit var memberRepository: MemberRepository

    @Test
    fun `회원을 저장하고 조회한다`() {
        val saved = memberRepository.save(Member("user@example.com"))

        val found = memberRepository.findById(saved.id).orElse(null)

        assertNotNull(found)
        assertEquals("user@example.com", found.email)
    }

    @Test
    fun `수정하면 updatedAt이 갱신된다`() {
        val saved = memberRepository.saveAndFlush(Member("user@example.com"))
        val firstUpdatedAt = saved.updatedAt
        Thread.sleep(10)

        saved.updateNickname(Nickname("바다"))
        memberRepository.saveAndFlush(saved)

        assertTrue(saved.updatedAt.isAfter(firstUpdatedAt))
        assertNotNull(saved.createdAt)
    }
}
