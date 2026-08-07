package com.nexters.gamss.llm.selection

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterSelectorTest {
    private val selector = CharacterSelector()

    @Test
    fun `뽑힌 total은 characterCount와 tikitakaCount로 항상 전부 소진된다`() {
        repeat(1_000) { seed ->
            // select()가 가장 먼저 뽑는 값과 동일한 시드로 total(1~3)을 독립적으로 재현한다.
            val expectedTotal = Random(seed.toLong()).nextInt(1, 4)
            val selection = CharacterSelector(Random(seed.toLong())).select()

            val actualTotal = selection.characters.size + selection.tikitakaCount
            assertEquals(expectedTotal, actualTotal, "seed=$seed: total=$expectedTotal 인데 실제로는 $actualTotal 만 채워짐")
        }
    }

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
    fun `캐릭터가 2명 이상이면 티키타카가 나오는 경우도 있다`() {
        val hasTikitaka =
            (1..1_000).any {
                val selection = selector.select()
                selection.tikitakaCount > 0
            }

        assertTrue(hasTikitaka, "1000번을 돌려도 티키타카가 한 번도 안 나옴")
    }

    @Test
    fun `선택된 캐릭터는 중복이 없다`() {
        repeat(1_000) {
            val selection = selector.select()

            assertEquals(selection.characters.size, selection.characters.toSet().size)
        }
    }
}
