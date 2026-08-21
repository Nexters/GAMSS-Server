package com.nexters.gamss.card.domain

import com.nexters.gamss.card.service.ShareTokenGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareTokenTest {
    @Test
    fun `22자 base64url 만 토큰이 된다`() {
        assertEquals("Zm9vYmFyYmF6cXV4MTIzNA", ShareToken("Zm9vYmFyYmF6cXV4MTIzNA").value)
        assertEquals("-_09azAZ--__abcdefghij", ShareToken("-_09azAZ--__abcdefghij").value)
    }

    @Test
    fun `길이가 다르면 토큰이 아니다`() {
        assertFailsWith<IllegalArgumentException> { ShareToken("Zm9vYmFyYmF6cXV4MTIzN") }
        assertFailsWith<IllegalArgumentException> { ShareToken("Zm9vYmFyYmF6cXV4MTIzNAA") }
    }

    /** URL 경로에 들어가는 값이라 base64url 밖 문자는 받지 않는다. */
    @Test
    fun `base64url 밖의 문자는 토큰이 아니다`() {
        assertFailsWith<IllegalArgumentException> { ShareToken("Zm9vYmFyYmF6cXV4MTIzN/") }
        assertFailsWith<IllegalArgumentException> { ShareToken("Zm9vYmFyYmF6cXV4MTIzN+") }
        assertFailsWith<IllegalArgumentException> { ShareToken("한글한글한글한글한글한글한글한글한글한글한글") }
    }

    @Test
    fun `parseOrNull 은 형식이 틀리면 null 을 준다`() {
        assertNotNull(ShareToken.parseOrNull("Zm9vYmFyYmF6cXV4MTIzNA"))
        assertNull(ShareToken.parseOrNull("짧음"))
    }

    /** 대소문자를 구분한다 — 컬럼도 같은 이유로 utf8mb4_bin 이다(V35). */
    @Test
    fun `대소문자만 다른 토큰은 다른 값이다`() {
        assertTrue(ShareToken("aBcDeFgHiJkLmNoPqRsTuV") != ShareToken("AbCdEfGhIjKlMnOpQrStUv"))
    }

    @Test
    fun `생성기는 매번 다른 토큰을 만든다`() {
        val generator = ShareTokenGenerator()

        val tokens = (1..500).map { generator.generate().value }

        assertEquals(500, tokens.toSet().size, "난수 토큰이 겹치면 안 된다")
        assertTrue(tokens.all { it.length == ShareToken.LENGTH })
    }
}
