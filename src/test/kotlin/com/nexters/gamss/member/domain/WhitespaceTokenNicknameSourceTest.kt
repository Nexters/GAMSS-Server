package com.nexters.gamss.member.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhitespaceTokenNicknameSourceTest {
    private val source = WhitespaceTokenNicknameSource()

    @Test
    fun `첫 단어가 규칙에 맞으면 첫 단어를 쓴다`() {
        assertEquals(Nickname("John"), source.suggest("John Alexander Smith"))
    }

    @Test
    fun `첫 단어가 너무 짧으면 다음 단어를 시도한다`() {
        assertEquals(Nickname("Smith"), source.suggest("J Smith"))
    }

    @Test
    fun `모든 단어가 규칙에 어긋나면 null이다`() {
        assertNull(source.suggest("J K"))
    }

    @Test
    fun `공백이 없으면 원본 전체를 한 단어로 시도한다`() {
        assertEquals(Nickname("홍길동"), source.suggest("홍길동"))
    }

    @Test
    fun `이름이 없으면 null이다`() {
        assertNull(source.suggest(null))
    }
}
