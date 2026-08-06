package com.nexters.gamss.member.repository

import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.MemberStatus
import com.nexters.gamss.member.domain.Nickname
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun `검색어로 이메일·이름·닉네임을 부분 일치 조회한다`() {
        memberRepository.save(Member("bada@example.com", "김바다", Nickname("바다")))
        memberRepository.save(Member("hana@example.com", "이하늘", Nickname("하늘")))

        val byEmail = memberRepository.search("bada", null, PageRequest.of(0, 10))
        val byName = memberRepository.search("김", null, PageRequest.of(0, 10))
        val byNickname = memberRepository.search("하늘", null, PageRequest.of(0, 10))

        assertEquals(1, byEmail.totalElements)
        assertEquals("bada@example.com", byEmail.content.first().email)
        assertEquals(1, byName.totalElements)
        assertEquals("김바다", byName.content.first().name)
        assertEquals(1, byNickname.totalElements)
        val nicknameHit = byNickname.content.first()
        assertEquals("하늘", nicknameHit.nickname?.value)
    }

    @Test
    fun `검색어가 null이면 전체를 조회한다`() {
        memberRepository.save(Member("a@example.com"))
        memberRepository.save(Member("b@example.com"))

        val all = memberRepository.search(null, null, PageRequest.of(0, 10))

        assertEquals(2, all.totalElements)
    }

    @Test
    fun `상태로 필터링한다`() {
        memberRepository.save(Member("active@example.com"))
        val withdrawn = memberRepository.save(Member("left@example.com").apply { withdraw() })

        val actives = memberRepository.search(null, MemberStatus.ACTIVE, PageRequest.of(0, 10))
        val withdrawns = memberRepository.search(null, MemberStatus.WITHDRAWN, PageRequest.of(0, 10))

        assertEquals(1, actives.totalElements)
        assertEquals("active@example.com", actives.content.first().email)
        assertEquals(1, withdrawns.totalElements)
        // 탈퇴하면 개인 식별정보를 비우므로 이메일이 아니라 id 로 식별한다.
        assertEquals(withdrawn.id, withdrawns.content.first().id)
        assertNull(withdrawns.content.first().email)
    }
}
