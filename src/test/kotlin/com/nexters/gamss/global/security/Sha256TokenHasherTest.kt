package com.nexters.gamss.global.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class Sha256TokenHasherTest {
    private val hasher = Sha256TokenHasher()

    @Test
    fun `같은 토큰은 항상 같은 해시가 된다`() {
        assertEquals(hasher.hash("token"), hasher.hash("token"))
    }

    @Test
    fun `다른 토큰은 다른 해시가 된다`() {
        assertNotEquals(hasher.hash("token-a"), hasher.hash("token-b"))
    }

    @Test
    fun `해시는 원본을 노출하지 않으며 64자리 16진수다`() {
        val hashed = hasher.hash("super-secret-refresh-token")

        assertNotEquals("super-secret-refresh-token", hashed)
        assertEquals(64, hashed.length)
        assertEquals(true, hashed.all { it in "0123456789abcdef" })
    }

    @Test
    fun `SHA-256 표준 벡터와 일치한다`() {
        // echo -n "abc" | sha256sum
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hasher.hash("abc"))
    }
}
