package com.nexters.gamss.member.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RandomNicknameSourceTest {
    @Test
    fun `이름과 무관하게 형용사-동물 조합의 닉네임을 만든다`() {
        val source = RandomNicknameSource()
        val possibleCombinations =
            RandomNicknameSource.ADJECTIVES.flatMap { adjective ->
                RandomNicknameSource.ANIMALS.map { animal -> Nickname(adjective + animal) }
            }

        val nickname = source.suggest("아무 이름이나 넣어도 상관없다 진짜로")

        assertTrue(possibleCombinations.contains(nickname))
    }

    @Test
    fun `같은 난수를 주입하면 같은 닉네임을 만든다`() {
        val rng = Random(seed = 42)
        val expected = Nickname(RandomNicknameSource.ADJECTIVES.random(rng) + RandomNicknameSource.ANIMALS.random(rng))

        val actual = RandomNicknameSource(Random(seed = 42)).suggest(null)

        assertEquals(expected, actual)
    }

    @Test
    fun `모든 형용사-동물 조합이 닉네임 규칙을 만족한다`() {
        // 단어 목록을 늘릴 때 실수로 규칙을 어기는 조합이 섞이지 않는지 지킨다.
        for (adjective in RandomNicknameSource.ADJECTIVES) {
            for (animal in RandomNicknameSource.ANIMALS) {
                Nickname(adjective + animal)
            }
        }
    }
}
