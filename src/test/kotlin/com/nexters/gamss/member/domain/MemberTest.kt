package com.nexters.gamss.member.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberTest {
    @Test
    fun `이메일로 회원을 생성한다`() {
        val member = Member("user@example.com")

        assertEquals("user@example.com", member.email)
    }

    @Test
    fun `생성 시 생성 시각이 기록되고 식별자 기본값은 0이다`() {
        val member = Member("user@example.com")

        assertNotNull(member.createdAt)
        assertEquals(0L, member.id)
    }

    @Test
    fun `이메일 없이도 회원을 생성할 수 있다`() {
        assertNull(Member().email)
    }

    @Test
    fun `이메일을 수정한다`() {
        val member = Member("old@example.com")

        member.updateEmail("new@example.com")

        assertEquals("new@example.com", member.email)
    }

    @Test
    fun `닉네임을 수정한다`() {
        val member = Member("user@example.com")

        member.updateNickname(Nickname("바다"))

        assertEquals(Nickname("바다"), member.nickname)
    }

    @Test
    fun `생성 직후에는 활성 상태이며 탈퇴하지 않았다`() {
        val member = Member("user@example.com")

        assertEquals(MemberStatus.ACTIVE, member.status)
        assertFalse(member.isWithdrawn())
        assertNull(member.deletedAt)
    }

    @Test
    fun `탈퇴하면 상태가 WITHDRAWN이 되고 삭제 시각이 기록된다`() {
        val member = Member("user@example.com")

        member.withdraw()

        assertEquals(MemberStatus.WITHDRAWN, member.status)
        assertTrue(member.isWithdrawn())
        assertNotNull(member.deletedAt)
    }

    @Test
    fun `탈퇴하면 개인 식별정보를 비운다`() {
        val member = Member("user@example.com", "홍길동", Nickname("걱정인형"))

        member.withdraw()

        assertNull(member.email)
        assertNull(member.name)
        assertNull(member.nickname)
    }
}
