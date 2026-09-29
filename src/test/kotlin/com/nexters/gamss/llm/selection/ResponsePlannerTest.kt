package com.nexters.gamss.llm.selection

import com.nexters.gamss.emotion.domain.EmotionType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponsePlannerTest {
    private val characterSelector = mockk<CharacterSelector>()
    private val planner = ResponsePlanner(characterSelector, VocativeAddresseeResolver())
    private val randomSelection = CharacterSelection(listOf(EmotionType.ANGER, EmotionType.GRUMPY), 1)

    @Test
    fun `아무도 부르지 않았으면 지금처럼 무작위로 고른다`() {
        every { characterSelector.select(emptySet()) } returns randomSelection

        val plan = planner.plan("오늘 좀 피곤했어", emptySet())

        assertEquals(randomSelection, plan.selection)
        assertEquals(Addressees.NONE, plan.addressees)
    }

    @Test
    fun `이 방의 캐릭터를 부르면 그 캐릭터 혼자 답하고 티키타카는 없다`() {
        val plan = planner.plan("기쁨아 그게 무슨소리야", emptySet())

        assertEquals(CharacterSelection(listOf(EmotionType.JOY), 0), plan.selection)
        verify(exactly = 0) { characterSelector.select(any()) }
    }

    @Test
    fun `두 캐릭터를 함께 부르면 각자 답하고 티키타카는 없다`() {
        val plan = planner.plan("기쁨아 분노 얘기 들었어? 슬픔아 너는?", emptySet())

        assertEquals(CharacterSelection(listOf(EmotionType.JOY, EmotionType.SADNESS), 0), plan.selection)
    }

    @Test
    fun `막아둔 캐릭터만 부르면 막아둔 캐릭터를 뺀 무작위 선택에 부른 사실만 남긴다`() {
        val excluded = setOf(EmotionType.SADNESS)
        every { characterSelector.select(excluded) } returns randomSelection

        val plan = planner.plan("슬픔아 어디 갔어", excluded)

        assertEquals(randomSelection, plan.selection)
        assertEquals(Addressees(present = emptyList(), absent = listOf(EmotionType.SADNESS)), plan.addressees)
    }

    @Test
    fun `이 방의 캐릭터와 막아둔 캐릭터를 함께 부르면 이 방의 캐릭터만 답한다`() {
        val plan = planner.plan("기쁨아 슬픔아 둘 다 들어봐", setOf(EmotionType.SADNESS))

        assertEquals(CharacterSelection(listOf(EmotionType.JOY), 0), plan.selection)
        assertEquals(Addressees(present = listOf(EmotionType.JOY), absent = listOf(EmotionType.SADNESS)), plan.addressees)
    }
}
