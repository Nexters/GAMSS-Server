package com.nexters.gamss.llm.selection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterSelectorTest {
    private val selector = CharacterSelector()

    @Test
    fun `캐릭터 수와 티키타카 수의 합은 항상 1~3이다`() {
        repeat(1_000) {
            val selection = selector.select()

            val total = selection.characters.size + selection.tikitakaCount
            assertTrue(total in 1..3, "총합이 1~3 범위를 벗어남: $total")
            assertTrue(selection.characters.size >= 1, "캐릭터 수가 1 미만으로 나옴")
        }
    }

    @Test
    fun `캐릭터가 2명 미만이면 티키타카는 0개다`() {
        repeat(1_000) {
            val selection = selector.select()

            if (selection.characters.size < 2) {
                assertEquals(0, selection.tikitakaCount, "캐릭터가 2명 미만인데 티키타카가 존재함")
            }
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
