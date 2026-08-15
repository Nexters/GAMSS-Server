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
    fun `개행은 공백으로 접어 한 줄로 만든다`() {
        // '한 줄' 보장이 생성기의 파싱 검증에만 있으면 구현을 갈아끼울 때 사라지고, 읽기 경로로
        // 들어오는 옛 카드 요약에는 애초에 실패시킬 대상이 없다(이미 저장된 값이다).
        assertEquals(
            "오늘 힘든 일이 있었어요 그래도 괜찮아요",
            CardSummary.normalize("오늘 힘든 일이 있었어요\n그래도 괜찮아요"),
        )
    }

    @Test
    fun `연속된 공백류는 한 칸으로 접는다`() {
        assertEquals(
            "오늘 힘들었어요 그래도 괜찮아요",
            CardSummary.normalize("오늘   힘들었어요 \r\n\t 그래도  괜찮아요"),
        )
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

    @Test
    fun `이모지가 상한을 넘겨도 서로게이트 쌍이 갈라지지 않는다`() {
        // String.length는 UTF-16 유닛이라 이모지가 2로 세진다. 그 단위로 자르면 쌍 한가운데가
        // 잘려 깨진 문자가 카드에 그대로 남는다(카드는 재생성이 안 된다).
        val normalized = CardSummary.normalize("\uD83D\uDE00".repeat(60))

        // 고립된 서로게이트는 UTF-8로 인코딩할 때 대체 문자로 바뀌므로 왕복이 깨진다.
        assertEquals(normalized, String(normalized.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        assertTrue(CardSummary.graphemeCount(normalized) <= CardSummary.MAX_LENGTH)
    }

    @Test
    fun `피부톤 이모지가 조합에서 갈라지지 않는다`() {
        val normalized = CardSummary.normalize("\uD83D\uDC4D\uD83C\uDFFD".repeat(60))

        assertEquals(normalized, String(normalized.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        assertTrue(CardSummary.graphemeCount(normalized) <= CardSummary.MAX_LENGTH)
    }

    @Test
    fun `ZWJ 가족 이모지가 한 글자로 세어지고 갈라지지 않는다`() {
        // 👨‍👩‍👧 — 코드 포인트 5개, UTF-16 8유닛이지만 화면에서는 한 글자다.
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        assertEquals(1, CardSummary.graphemeCount(family))

        val normalized = CardSummary.normalize(family.repeat(60))

        assertEquals(normalized, String(normalized.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        assertTrue(CardSummary.graphemeCount(normalized) <= CardSummary.MAX_LENGTH)
    }

    @Test
    fun `상한 이내의 이모지 문장은 그대로 둔다`() {
        // 유닛으로 재면 상한을 넘는다고 오판해 멀쩡한 문장을 자르게 된다.
        val line = "오늘 하루종일 집 밖에 안 나갔어요 \uD83D\uDE00".repeat(1)

        assertEquals(line, CardSummary.normalize(line))
    }
}
