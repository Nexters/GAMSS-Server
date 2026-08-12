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
        // 신규 생성에서 빠진 캐릭터도 과거 댓글의 답글·카드 생성에 쓰이므로 검사 대상은 SELECTABLE이 아니라 전체다.
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
    fun `신규 생성에서 빠진 다정이의 번역도 유지된다`() {
        // 과거에 다정이가 단 댓글에 유저가 답글을 달면 그 캐릭터로 재응답해야 한다 — 매핑을 지우면 그 경로가 죽는다.
        assertEquals("dajeong", PromptCharacterId.of(EmotionType.WARM).promptId)
        assertEquals(EmotionType.WARM, PromptCharacterId.fromPromptId("dajeong").emotionType)
    }

    @Test
    fun `모르는 프롬프트 id는 생성 실패로 던진다`() {
        assertFailsWith<CommentGenerationFailedException> { PromptCharacterId.fromPromptId("unknown") }
    }
}
