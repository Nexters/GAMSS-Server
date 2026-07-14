package com.nexters.gamss.member.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NicknameTest {
    @Test
    fun `유효한 값으로 닉네임을 생성한다`() {
        assertEquals("바다", Nickname("바다").value)
    }

    @Test
    fun `최대 길이까지 허용한다`() {
        val value = "가".repeat(Nickname.MAX_LENGTH)

        assertEquals(value, Nickname(value).value)
    }

    @Test
    fun `공백이면 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { Nickname(" ") }
    }

    @Test
    fun `최대 길이를 넘으면 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { Nickname("가".repeat(Nickname.MAX_LENGTH + 1)) }
    }

    @Test
    fun `값이 같으면 동등하다`() {
        assertEquals(Nickname("바다"), Nickname("바다"))
    }
}
