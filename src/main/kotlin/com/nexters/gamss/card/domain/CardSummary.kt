package com.nexters.gamss.card.domain

import java.text.BreakIterator

/**
 * 카드에 남는 한 줄의 길이 규칙. 값은 [Card.summary]에 문자열로 저장되지만, "사용자가 보는 글자 수로
 * [MAX_LENGTH]자를 넘지 않는다"는 불변식은 이 한 곳이 소유한다.
 *
 * 길이를 생성기가 아니라 여기서 다루는 이유는 두 가지다. 생성기는 구조 검증(JSON 파싱·한 줄 여부)까지만
 * 맡는 외부 경계 어댑터라 구현을 갈아끼우면 길이 보장이 같이 사라지고, 프롬프트가 지시하는 45자는
 * 어디까지나 유도값이라 실제 상한과 분리돼 있어야 하기 때문이다.
 *
 * LLM이 상한을 넘겼을 때 카드 생성을 실패시키지 않고 자른다 — 이미 만들어진 멀쩡한 문장이 조금 긴 것뿐이고,
 * 카드는 대화 하나당 한 번만 만들 수 있어 실패시키면 그날 기록이 통째로 남지 않는다.
 */
object CardSummary {
    /** 카드 UI가 감당하는 한 줄 길이(공백 포함, 사용자가 보는 글자 수). */
    const val MAX_LENGTH = 50

    /** 말줄임표를 붙이고도 [MAX_LENGTH]를 지키려면 본문은 한 글자를 양보해야 한다. */
    private const val BODY_LIMIT = MAX_LENGTH - 1

    private const val ELLIPSIS = "…"

    private val WHITESPACE = Regex("\\s+")

    /**
     * 어절 경계가 문장 앞쪽에 있으면 자를 때 내용이 통째로 날아간다. 그 경우엔 어절을 무시하고
     * 글자 수로 자르는 편이 남는 정보가 많다.
     */
    private const val MIN_WORD_BOUNDARY = BODY_LIMIT / 2

    /**
     * 저장 가능한 한 줄로 다듬는다 — 개행·연속 공백을 한 칸으로 접고, [MAX_LENGTH]를 넘으면 어절
     * 경계에서 자른 뒤 말줄임표를 붙인다. 문장 중간에서 끊기지 않게 하려는 것이다.
     *
     * '한 줄'까지 여기서 흡수하는 것은 길이와 같은 이유다 — 생성기의 파싱 검증에만 두면 구현을
     * 갈아끼울 때 보장이 사라지고, 이 값은 읽기 경로에서 옛 카드에도 적용된다(그 시절 요약에는
     * 개행이 들어 있을 수 있다).
     */
    fun normalize(raw: String): String {
        val trimmed = raw.replace(WHITESPACE, " ").trim()
        val graphemes = graphemesOf(trimmed)
        if (graphemes.size <= MAX_LENGTH) {
            return trimmed
        }
        val head = graphemes.take(BODY_LIMIT)
        val lastSpace = head.indexOfLast { it == " " }
        val body = if (lastSpace >= MIN_WORD_BOUNDARY) head.subList(0, lastSpace) else head
        return body.joinToString("").trimEnd() + ELLIPSIS
    }

    /**
     * 사용자가 보는 글자 수(그래핌 클러스터). [String.length]는 UTF-16 유닛 수라 이모지가 2자로
     * 계산되고, 그 단위로 자르면 서로게이트 쌍 한가운데가 잘려 깨진 문자가 남는다
     * ([com.nexters.gamss.member.domain.Nickname]이 같은 이유로 그래핌을 센다).
     */
    fun graphemeCount(value: String): Int = graphemesOf(value).size

    /**
     * 그래핌 클러스터 단위로 쪼갠다. 개수만 필요한 [graphemeCount]와 달리 [normalize]는 자를 위치를
     * 그래핌으로 잡아야 해서 조각 자체가 필요하다 — 피부톤·ZWJ 조합 이모지가 중간에서 갈라지지 않는다.
     */
    private fun graphemesOf(value: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(value)
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            result.add(value.substring(start, end))
            start = end
            end = iterator.next()
        }
        return result
    }
}
