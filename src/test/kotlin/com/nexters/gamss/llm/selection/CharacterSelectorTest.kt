package com.nexters.gamss.llm.selection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterSelectorTest {
    private val selector = CharacterSelector()

    @Test
    fun `캐릭터 수와 티키타카 수의 합은 항상 4~6이고 티키타카는 최소 1개다`() {
        repeat(1_000) {
            val selection = selector.select()

            val total = selection.characters.size + selection.tikitakaCount
            assertTrue(total in 4..6, "총합이 4~6 범위를 벗어남: $total")
            assertTrue(selection.tikitakaCount >= 1, "티키타카가 0개로 나옴")
            assertTrue(selection.characters.size >= 3, "캐릭터 수가 3 미만으로 나옴")
        }
    }

    @Test
    fun `선택된 캐릭터는 중복이 없다`() {
        repeat(1_000) {
            val selection = selector.select()

            assertEquals(selection.characters.size, selection.characters.toSet().size)
        }
    }
}
