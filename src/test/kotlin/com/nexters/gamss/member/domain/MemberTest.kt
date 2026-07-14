package com.nexters.gamss.member.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemberTest {
    @Test
    fun `이메일로 회원을 생성한다`() {
        val member = Member("user@example.com")

        assertEquals("user@example.com", member.email)
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
}
