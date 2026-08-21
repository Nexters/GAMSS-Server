package com.nexters.gamss.global.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlindIndexerTest {
    private val indexer = BlindIndexer(testProperties())

    @Test
    fun `같은 평문은 항상 같은 토큰열이 된다`() {
        // 저장과 검색이 각각 토큰을 만들어 맞추므로, 결정론적이지 않으면 검색이 아예 성립하지 않는다.
        assertEquals(indexer.tokenize("힘들었다"), indexer.tokenize("힘들었다"))
    }

    @Test
    fun `n글자 어절에서 n-1개의 토큰이 나온다`() {
        assertEquals(3, indexer.tokenize("힘들었다").size, "힘들·들었·었다")
    }

    @Test
    fun `공백을 넘는 bigram은 만들지 않는다`() {
        // ngram 파서와 같은 규칙이다. 어긋나면 지금 검색과 결과가 달라진다.
        assertEquals(indexer.tokenize("회사") + indexer.tokenize("스트레스"), indexer.tokenize("회사 스트레스"))
    }

    @Test
    fun `1글자 어절에서는 토큰이 나오지 않는다`() {
        assertTrue(indexer.tokenize("가 나 다").isEmpty(), "토큰 크기가 2라 1글자는 담을 수 없다")
        assertNull(indexer.toIndexValue("가 나 다"), "검색될 수 없는 값은 저장하지 않는다")
    }

    @Test
    fun `영문 대소문자를 구분하지 않는다`() {
        assertEquals(indexer.tokenize("Hello"), indexer.tokenize("hello"))
    }

    @Test
    fun `자모가 분리된 입력도 조합형과 같은 토큰이 된다`() {
        // iOS·macOS 입력이 분리형으로 들어오는 경우가 있다. 정규화하지 않으면 눈에 같은 글자가
        // 다른 토큰이 되어, 저장은 됐는데 검색은 안 되는 상태가 된다.
        val composed = "\uD55C\uAE00"
        val decomposed = "\u1112\u1161\u11AB\u1100\u1173\u11AF"

        assertEquals(indexer.tokenize(composed), indexer.tokenize(decomposed))
    }

    @Test
    fun `토큰열은 원문 순서를 보존한다`() {
        // 순서가 남아야 FULLTEXT 구문 검색으로 부분 일치를 재현할 수 있다.
        assertEquals(indexer.tokenize("가나").single(), indexer.tokenize("가나다").first())
        assertNotEquals(indexer.tokenize("가나").single(), indexer.tokenize("나가").single())
    }

    @Test
    fun `인덱스 키가 다르면 다른 토큰이 나온다`() {
        val other = BlindIndexer(testProperties(indexKey = OTHER_INDEX_KEY))

        assertNotEquals(indexer.tokenize("힘들었다"), other.tokenize("힘들었다"))
    }

    @Test
    fun `토큰은 16자리 hex다`() {
        // 기본 파서 FULLTEXT가 공백으로만 자르므로, 토큰에 구분자가 될 문자가 섞이면 안 된다.
        assertTrue(indexer.tokenize("힘들었다").all { it.matches(Regex("[0-9a-f]{16}")) })
    }

    @Test
    fun `이모지는 한 글자로 센다`() {
        // 코드 유닛으로 자르면 서로게이트 쌍이 쪼개져, 눈에는 한 글자인데 토큰이 반쪽으로 나온다.
        val grin = "\uD83D\uDE00"
        val beam = "\uD83D\uDE01"

        assertTrue(indexer.tokenize(grin).isEmpty(), "이모지 하나는 1글자라 bigram 이 안 나온다")
        assertEquals(1, indexer.tokenize(grin + beam).size, "이모지 둘이면 bigram 하나다")
        assertNotEquals(indexer.tokenize(grin + beam), indexer.tokenize(beam + grin), "순서가 다르면 토큰도 달라야 한다")
    }

    @Test
    fun `토큰열은 토큰을 공백으로 이어 붙인 값이다`() {
        // FULLTEXT 기본 파서가 공백으로만 자르므로 이 구분자가 곧 검색의 전제다.
        val tokens = indexer.tokenize("힘들었다")

        assertEquals(tokens.joinToString(" "), indexer.toIndexValue("힘들었다"))
    }
}
