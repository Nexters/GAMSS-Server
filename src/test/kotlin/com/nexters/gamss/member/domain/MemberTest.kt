package com.nexters.gamss.member.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemberTest {
    @Test
    fun `회원을 생성한다`() {
        val member = Member(OAuthProvider.GOOGLE, "google-sub-1", "user@example.com")

        assertEquals(OAuthProvider.GOOGLE, member.provider)
        assertEquals("google-sub-1", member.providerId)
        assertEquals("user@example.com", member.email)
    }

    @Test
    fun `이메일 없이도 회원을 생성할 수 있다`() {
        val member = Member(OAuthProvider.APPLE, "apple-sub-1")

        assertNull(member.email)
    }

    @Test
    fun `이메일을 수정한다`() {
        val member = Member(OAuthProvider.APPLE, "apple-sub-2")

        member.updateEmail("changed@example.com")

        assertEquals("changed@example.com", member.email)
    }
}
