package com.nexters.gamss.global.crypto

import org.springframework.stereotype.Component
import java.text.Normalizer
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 평문을 검색 가능한 토큰열(블라인드 인덱스)로 바꾼다. 암호화된 컬럼은 그대로 검색할 수 없으므로,
 * 원문 대신 이 토큰열에 FULLTEXT 인덱스를 걸고 검색한다.
 *
 * 원문을 bigram으로 쪼개 HMAC-SHA256 앞 8바이트를 hex로 만든 뒤 **원문 순서대로** 이어 붙인다.
 * 기본 파서 FULLTEXT의 구문 검색(`"t1 t2"`)이 인접·순서를 요구하므로, ngram 파서가 하던 부분 일치가
 * 그대로 재현된다 — 토큰을 집합으로만 맞추는 방식에서 생기는 오탐이 없다.
 *
 * **저장과 검색이 반드시 같은 규칙을 거쳐야 한다.** 정규화·대소문자 접기·어절 분리 중 하나라도
 * 어긋나면 방금 저장한 값조차 검색되지 않는다. 그래서 두 경로 모두 [tokenize] 하나만 쓴다.
 *
 * 유출 시 한계도 적어둔다 — 토큰은 HMAC이라 키 없이 역산할 수 없지만, 한국어 bigram 빈도가 편중돼
 * 있어 인덱스가 통째로 새면 빈도 분석으로 흔한 조합을 추정할 수 있다. 순서까지 남으므로 추정한
 * 조각을 이어 붙이기도 쉽다. 인덱스 키를 암호화 키와 분리해 두는 이유가 이것이다
 * ([EncryptionProperties]) — 인덱스가 새도 암호문 자체는 열리지 않아야 한다.
 */
@Component
class BlindIndexer(
    properties: EncryptionProperties,
) {
    private val key = SecretKeySpec(properties.decodedIndexKey, ALGORITHM)

    /**
     * 컬럼에 저장할 토큰열. 토큰이 하나도 안 나오면(전부 1글자 어절인 경우) null을 준다 —
     * 어차피 어떤 검색어로도 걸릴 수 없는 값이라 빈 문자열을 넣어 인덱스를 늘릴 이유가 없다.
     */
    fun toIndexValue(text: String): String? = tokenize(text).joinToString(" ").ifBlank { null }

    /**
     * 정규화 → 어절 분리 → bigram → HMAC 앞 8바이트.
     *
     * NFKC로 먼저 정규화한다. iOS·macOS 입력이 자모가 분리된 형태로 들어오는 경우가 있어, 정규화
     * 없이 자르면 눈에 같아 보이는 글자가 다른 토큰이 된다(저장은 분리형, 검색어는 조합형이면 못 찾는다).
     *
     * 공백을 넘는 bigram은 만들지 않는다 — ngram 파서와 같은 규칙이다. 그래서 "회사 스트레스"처럼
     * 어절이 떨어진 검색어는 원문에서도 두 어절이 붙어 있어야 걸리는데, 지금 검색도 똑같이 동작한다.
     * 1글자 어절에서 토큰이 안 나오는 것도 ngram(토큰 크기 2)과 같다.
     */
    fun tokenize(text: String): List<String> {
        // Mac 은 doFinal 뒤 초기화 상태로 돌아가므로 호출 하나 안에서는 재사용할 수 있다. bigram 마다
        // 새로 만들면 500자 메시지 하나에 provider 조회가 499번 일어난다. 지역 변수라 스레드 안전은 그대로다.
        val mac = Mac.getInstance(ALGORITHM).apply { init(key) }
        return Normalizer
            .normalize(text, Normalizer.Form.NFKC)
            .lowercase()
            .split(WHITESPACE)
            .flatMap { segment -> segment.bigrams().map { bigram -> mac.hash(bigram) } }
    }

    /**
     * 코드 포인트 단위로 자른다. `String.windowed` 는 UTF-16 코드 유닛 기준이라 이모지처럼 보조 평면에
     * 있는 글자의 서로게이트 쌍이 두 조각으로 쪼개진다 — 눈에는 한 글자인데 토큰은 반쪽이 되고,
     * 앞 글자가 같은 이모지끼리 같은 조각을 공유한다. MySQL ngram 파서도 코드 포인트를 센다.
     */
    private fun String.bigrams(): List<String> {
        val points = codePoints().toArray()
        return (0..points.size - BIGRAM_SIZE).map { start ->
            buildString { repeat(BIGRAM_SIZE) { appendCodePoint(points[start + it]) } }
        }
    }

    private fun Mac.hash(bigram: String): String = HEX.formatHex(doFinal(bigram.toByteArray()), 0, TOKEN_SIZE_BYTES)

    companion object {
        private const val ALGORITHM = "HmacSHA256"

        /** MySQL ngram 파서의 기본 토큰 크기와 맞춘다. 다르게 두면 최소 검색어 길이 규약이 어긋난다. */
        private const val BIGRAM_SIZE = 2

        /**
         * 해시를 8바이트로 자른다. 충돌이 나도 검색 오탐으로만 나타나고(암호문 복호화와는 무관한 키다)
         * 인덱스 컬럼 크기는 절반이 된다.
         */
        private const val TOKEN_SIZE_BYTES = 8

        private val WHITESPACE = Regex("\\s+")
        private val HEX: HexFormat = HexFormat.of()
    }
}
