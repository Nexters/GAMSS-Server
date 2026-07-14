package com.nexters.gamss.auth.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefreshTokenTest {
    @Test
    fun `회전하면 토큰이 교체된다`() {
        val refreshToken = RefreshToken(memberId = 1L, token = "old")

        refreshToken.rotate("new")

        assertEquals("new", refreshToken.token)
    }

    @Test
    fun `같은 토큰이면 일치한다`() {
        val refreshToken = RefreshToken(memberId = 1L, token = "abc")

        assertTrue(refreshToken.matches("abc"))
        assertFalse(refreshToken.matches("xyz"))
    }
}
