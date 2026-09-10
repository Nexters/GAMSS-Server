package com.nexters.gamss.member.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TruncatedNameNicknameSourceTest {
    private val source = TruncatedNameNicknameSource()

    @Test
    fun `상한을 넘는 단일 단어를 그래핌 상한까지 잘라 쓴다`() {
        val name = "가".repeat(Nickname.MAX_LENGTH + 5)

        assertEquals(Nickname("가".repeat(Nickname.MAX_LENGTH)), source.suggest(name))
    }

    @Test
    fun `잘라도 최소 길이 미만이면 null이다`() {
        assertNull(source.suggest("가"))
    }

    @Test
    fun `이름이 없으면 null이다`() {
        assertNull(source.suggest(null))
    }

    @Test
    fun `공백만 있으면 null이다`() {
        assertNull(source.suggest("   "))
    }
}
