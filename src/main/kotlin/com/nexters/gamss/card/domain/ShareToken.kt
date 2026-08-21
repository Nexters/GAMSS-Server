package com.nexters.gamss.card.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 카드 공유 링크(`gamss.kr/c/{value}`)가 가리키는 토큰 값 객체.
 *
 * 카드 [Card.id] 를 URL 에 쓰지 않으려고 둔다 — id 는 AUTO_INCREMENT 라 `/c/1`, `/c/2` 로 남의
 * 카드를 순서대로 긁을 수 있는데, 카드의 요약은 컬럼 단위로 암호화한 값이라 순번 노출이 그 암호화를
 * 무의미하게 만든다.
 *
 * 값은 랜덤 16바이트를 패딩 없는 base64url 로 적은 22자다([ShareTokenGenerator]). 이 클래스는
 * 만들어진 값이 그 형식인지만 보장하고, 만드는 일은 하지 않는다 — 난수는 부를 때마다 답이 달라지는
 * 요소라 불변식만 다루는 도메인 밖에 둔다.
 */
@Embeddable
class ShareToken(
    @Column(name = "share_token", length = LENGTH)
    val value: String,
) {
    init {
        require(value.length == LENGTH) { "공유 토큰은 ${LENGTH}자여야 합니다." }
        require(value.all { it in ALLOWED }) { "공유 토큰은 base64url 문자만 쓸 수 있습니다." }
    }

    /**
     * 토큰은 대소문자를 구분한다. 컬럼도 같은 이유로 utf8mb4_bin 이다(V35) — 대소문자를 무시하면
     * 서로 다른 두 토큰이 같은 값이 되어 조회가 남의 카드를 돌려줄 수 있다.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShareToken) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        const val LENGTH = 22

        private val ALLOWED = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '_')

        /** 형식에 맞지 않으면 null. 사용자가 준 문자열(URL 경로)을 걸러 DB 조회까지 가지 않게 한다. */
        fun parseOrNull(value: String): ShareToken? = runCatching { ShareToken(value) }.getOrNull()
    }
}
