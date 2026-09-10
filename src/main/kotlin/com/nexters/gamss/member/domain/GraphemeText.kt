package com.nexters.gamss.member.domain

import java.text.BreakIterator

/**
 * 그래핌(사용자가 보는 글자) 단위 문자열 연산. [String.length]는 UTF-16 유닛 수라 이모지가
 * 2자로 계산되고, 코드 포인트로 세도 피부톤·ZWJ 조합 이모지는 여러 자로 계산된다. 여기서는
 * [BreakIterator]로 사용자가 실제로 보는 글자 수 기준으로 세고 자른다.
 */
object GraphemeText {
    fun count(value: String): Int {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(value)
        var count = 0
        while (iterator.next() != BreakIterator.DONE) {
            count++
        }
        return count
    }

    /** 앞에서부터 그래핌 [maxGraphemes]개까지만 남기고 자른다. 그래핌 경계를 지키므로 서로게이트 쌍·ZWJ 조합이 깨지지 않는다. */
    fun take(
        value: String,
        maxGraphemes: Int,
    ): String {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(value)
        var boundary = iterator.first()
        var count = 0
        while (boundary != BreakIterator.DONE) {
            if (count == maxGraphemes) {
                return value.substring(0, boundary)
            }
            boundary = iterator.next()
            count++
        }
        return value
    }
}
