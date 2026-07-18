package com.nexters.gamss.global.security

/**
 * 저장 전 토큰을 단방향 해시한다. DB가 유출돼도 저장된 값을 그대로 Bearer 토큰으로 재사용할 수 없게 한다.
 * 구현(SHA-256 등)은 교체 가능하다.
 */
interface TokenHasher {
    fun hash(token: String): String
}
