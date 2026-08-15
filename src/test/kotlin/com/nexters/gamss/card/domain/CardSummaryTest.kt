package com.nexters.gamss.card.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardSummaryTest {
    @Test
    fun `상한 이내면 그대로 둔다`() {
        val line = "우산을 안 챙겨서 옷이 다 젖어버렸어요"

        assertEquals(line, CardSummary.normalize(line))
    }

    @Test
    fun `앞뒤 공백은 없앤다`() {
        assertEquals("오늘 하루종일 집 밖에 안 나갔어요", CardSummary.normalize("  오늘 하루종일 집 밖에 안 나갔어요\t"))
    }

    @Test
    fun `정확히 상한 길이면 자르지 않는다`() {
        val line = "가".repeat(CardSummary.MAX_LENGTH)

        assertEquals(line, CardSummary.normalize(line))
    }

    @Test
    fun `상한을 넘으면 어절 경계에서 자르고 말줄임표를 붙인다`() {
        // 문장 중간에서 끊기면 카드에 그대로 남으므로, 자를 때는 어절이 살아 있어야 한다.
        val line = "오늘 팀장이 자기 할 일을 전부 떠넘긴 데다 야근까지 시켜서 정말 화가 나고 억울한 마음이 들어요"

        val normalized = CardSummary.normalize(line)

        assertTrue(normalized.length <= CardSummary.MAX_LENGTH)
        assertTrue(normalized.endsWith("…"))
        // 어절 경계에서 잘렸다면 말줄임표 앞은 공백이 아니라 완결된 어절이다.
        assertFalse(normalized.dropLast(1).endsWith(" "))
        assertTrue(line.startsWith(normalized.dropLast(1)))
    }

    @Test
    fun `공백이 없어 어절 경계를 찾을 수 없으면 글자 수로 자른다`() {
        // 경계를 못 찾았다고 실패시키면 카드가 통째로 안 만들어진다.
        val normalized = CardSummary.normalize("가".repeat(CardSummary.MAX_LENGTH + 20))

        assertEquals(CardSummary.MAX_LENGTH, normalized.length)
        assertTrue(normalized.endsWith("…"))
    }

    @Test
    fun `어절 경계가 문장 앞쪽에만 있으면 내용을 살리려고 글자 수로 자른다`() {
        // "짧은 어절 + 아주 긴 덩어리" 형태에서 첫 공백을 기준으로 자르면 내용이 거의 다 날아간다.
        val normalized = CardSummary.normalize("오늘 " + "가".repeat(CardSummary.MAX_LENGTH + 20))

        assertTrue(normalized.length > "오늘".length + 1)
        assertTrue(normalized.length <= CardSummary.MAX_LENGTH)
    }
}
