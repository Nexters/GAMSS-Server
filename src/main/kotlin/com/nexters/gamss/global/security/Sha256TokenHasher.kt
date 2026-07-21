package com.nexters.gamss.global.security

import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * SHA-256 기반 TokenHasher.
 *
 * 리프레시 토큰은 서명된 JWT라 이미 고엔트로피다 — 무차별 대입 위험이 없어 비밀번호용 느린 해시
 * (bcrypt 등)가 필요 없고, bcrypt 는 입력 72바이트 제한이 있어 그보다 긴 JWT 에 부적합하다.
 * 저장 유출 방지가 목적이므로 빠른 단방향 해시로 충분하다.
 */
@Component
class Sha256TokenHasher : TokenHasher {
    // MessageDigest 는 스레드 안전하지 않아 호출마다 새로 생성한다.
    override fun hash(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
