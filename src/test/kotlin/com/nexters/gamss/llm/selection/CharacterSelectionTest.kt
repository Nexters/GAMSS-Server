package com.nexters.gamss.llm.selection

import com.nexters.gamss.emotion.domain.EmotionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CharacterSelectionTest {
    @Test
    fun `고정 선택은 중복을 제거하고 순서를 보존한다`() {
        val selection = CharacterSelection.of(listOf(EmotionType.JOY, EmotionType.ANGER, EmotionType.JOY), 1)

        assertEquals(listOf(EmotionType.JOY, EmotionType.ANGER), selection.characters)
        assertEquals(1, selection.tikitakaCount)
    }

    @Test
    fun `캐릭터가 비어 있으면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { CharacterSelection.of(emptyList(), 0) }
    }

    @Test
    fun `티키타카는 캐릭터가 2명 이상일 때만 지정할 수 있다`() {
        assertFailsWith<IllegalArgumentException> { CharacterSelection.of(listOf(EmotionType.JOY), 1) }
    }

    @Test
    fun `음수 티키타카는 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { CharacterSelection.of(listOf(EmotionType.JOY, EmotionType.ANGER), -1) }
    }

    @Test
    fun `중복 제거 후 캐릭터가 1명이면 티키타카를 지정할 수 없다`() {
        // distinct가 티키타카 검사보다 먼저 적용되는 순서 의존성을 고정한다.
        assertFailsWith<IllegalArgumentException> { CharacterSelection.of(listOf(EmotionType.JOY, EmotionType.JOY), 1) }
    }
}
