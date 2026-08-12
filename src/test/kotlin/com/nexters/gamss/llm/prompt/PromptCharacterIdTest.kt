package com.nexters.gamss.llm.prompt

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PromptCharacterIdTest {
    @Test
    fun `모든 감정 캐릭터가 프롬프트 id를 가진다`() {
        // 매핑이 빠진 채로 of()를 부르면 NoSuchElementException으로 죽는다.
        EmotionType.entries.forEach { emotionType ->
            assertEquals(emotionType, PromptCharacterId.of(emotionType).emotionType, "$emotionType 의 프롬프트 id가 없다")
        }
    }

    @Test
    fun `슬픔이는 seulpeum으로 번역된다`() {
        assertEquals("seulpeum", PromptCharacterId.of(EmotionType.SADNESS).promptId)
        assertEquals(EmotionType.SADNESS, PromptCharacterId.fromPromptId("seulpeum").emotionType)
    }

    @Test
    fun `모르는 프롬프트 id는 생성 실패로 던진다`() {
        assertFailsWith<CommentGenerationFailedException> { PromptCharacterId.fromPromptId("unknown") }
    }
}
