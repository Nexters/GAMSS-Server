package com.nexters.gamss.card.controller.dto

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.emotion.domain.EmotionType
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class CardCalendarResponseTest {
    private val zone = ZoneId.of("Asia/Seoul")

    private fun card(
        emotion: EmotionType,
        date: LocalDate,
    ): Card =
        Card(
            memberId = 1L,
            conversationId = 1L,
            emotion = emotion,
            summary = "요약",
            message = "대사",
            conversationCreatedAt = date.atTime(12, 0).atZone(zone).toInstant(),
        )

    @Test
    fun `날짜별로 카드 감정을 묶어 오름차순으로 반환한다`() {
        val cards =
            listOf(
                card(EmotionType.ANGER, LocalDate.of(2026, 1, 2)),
                card(EmotionType.QUIRKY, LocalDate.of(2026, 1, 1)),
                card(EmotionType.ANXIETY, LocalDate.of(2026, 1, 1)),
            )

        val result = CardCalendarResponse.listFrom(cards)

        assertEquals(2, result.size)
        assertEquals(LocalDate.of(2026, 1, 1), result[0].date)
        assertEquals(listOf("QUIRKY", "ANXIETY"), result[0].emotions)
        assertEquals(LocalDate.of(2026, 1, 2), result[1].date)
        assertEquals(listOf("ANGER"), result[1].emotions)
    }

    @Test
    fun `KST 자정 직후 새벽 카드는 그날 날짜로 묶인다`() {
        val dawnDate = LocalDate.of(2026, 1, 5)
        val dawnInstant = dawnDate.atTime(0, 30).atZone(zone).toInstant()
        val dawn =
            Card(
                memberId = 1L,
                conversationId = 2L,
                emotion = EmotionType.WARM,
                summary = "요약",
                message = "대사",
                conversationCreatedAt = dawnInstant,
            )

        val result = CardCalendarResponse.listFrom(listOf(dawn))

        assertEquals(LocalDate.of(2026, 1, 5), result[0].date)
    }
}
