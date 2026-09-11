package com.nexters.gamss.member.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class GraphemeTextTest {
    @Test
    fun `일반 문자는 코드 유닛 수만큼 센다`() {
        assertEquals(2, GraphemeText.count("바다"))
    }

    @Test
    fun `조합 이모지도 한 글자로 센다`() {
        assertEquals(1, GraphemeText.count("👨‍👩‍👧‍👦"))
    }

    @Test
    fun `상한보다 짧으면 그대로 돌려준다`() {
        assertEquals("바다", GraphemeText.take("바다", 10))
    }

    @Test
    fun `그래핌 경계에서 자른다`() {
        assertEquals("가나다", GraphemeText.take("가나다라마", 3))
    }

    @Test
    fun `조합 이모지 중간에서 자르지 않는다`() {
        val family = "👨‍👩‍👧‍👦"

        assertEquals(family, GraphemeText.take("$family$family", 1))
    }
}
