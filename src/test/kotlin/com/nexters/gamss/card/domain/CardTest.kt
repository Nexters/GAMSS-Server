package com.nexters.gamss.card.domain

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    fun `요약이 상한을 넘으면 생성할 수 없다`() {
        // 길이를 맞추는 것은 CardSummary.normalize의 몫이다 — 그걸 거치지 않은 경로가 생기면 여기서 걸린다.
        assertFailsWith<IllegalArgumentException> { card(summary = "가".repeat(CardSummary.MAX_LENGTH + 1)) }
    }

    @Test
    fun `요약에 개행이 있으면 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { card(summary = "첫 줄\n둘째 줄") }
    }

    @Test
    fun `대사가 비어 있으면 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { card(message = " ") }
    }

    @Test
    fun `대사에 개행이 있으면 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { card(message = "첫 줄\n둘째 줄") }
    }

    @Test
    fun `삭제하면 삭제 시각이 기록된다`() {
        val card = card()

        card.delete()

        assertTrue(card.isDeleted())
        assertNotNull(card.deletedAt)
    }

    @Test
    fun `이미 삭제된 카드를 다시 삭제하면 CARD_ALREADY_DELETED`() {
        val card = card().apply { delete() }

        val exception = assertFailsWith<BusinessException> { card.delete() }

        assertEquals(ErrorCode.CARD_ALREADY_DELETED, exception.errorCode)
    }
}
