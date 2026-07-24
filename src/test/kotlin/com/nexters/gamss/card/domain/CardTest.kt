package com.nexters.gamss.card.domain

import com.nexters.gamss.emotion.domain.EmotionType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CardTest {
    private fun card(
        summary: String = "요약",
        message: String = "얘 오늘 건들면 안 됨.",
    ): Card =
        Card(
            memberId = 1L,
            conversationId = 1L,
            emotion = EmotionType.ANGER,
            summary = summary,
            message = message,
            conversationCreatedAt = Instant.parse("2026-07-23T00:00:00Z"),
        )

    @Test
    fun `요약이 비어 있으면 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { card(summary = " ") }
    }

    @Test
    fun `대사가 비어 있으면 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { card(message = " ") }
    }

    @Test
    fun `대사에 개행이 있으면 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { card(message = "첫 줄\n둘째 줄") }
    }
}
