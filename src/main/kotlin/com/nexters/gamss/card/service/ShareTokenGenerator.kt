package com.nexters.gamss.card.service

import com.nexters.gamss.card.domain.ShareToken
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

/**
 * 카드 공유 토큰을 만든다.
 *
 * 랜덤 16바이트(128비트)를 패딩 없는 base64url 로 적어 22자를 만든다. 추측으로 맞히는 것이
 * 불가능해야 하는 값이라 [SecureRandom] 을 쓴다 — 공유 링크는 인증 없이 열리므로 토큰을 아는 것이
 * 곧 카드를 볼 권한이다.
 *
 * 충돌 재시도 경로는 두지 않는다. 128비트에서 충돌은 사실상 일어나지 않고, 그래도 부딪히면
 * `uk_cards_share_token` 이 저장을 막아 조용히 남의 카드를 덮는 일은 생기지 않는다.
 */
@Component
class ShareTokenGenerator {
    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(): ShareToken {
        val bytes = ByteArray(BYTE_LENGTH)
        random.nextBytes(bytes)
        return ShareToken(encoder.encodeToString(bytes))
    }

    private companion object {
        /** 16바이트를 패딩 없는 base64url 로 적으면 [ShareToken.LENGTH] 인 22자가 된다. */
        const val BYTE_LENGTH = 16
    }
}
