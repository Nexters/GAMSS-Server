package com.nexters.gamss.auth.oauth

import com.nexters.gamss.member.domain.OAuthProvider

/**
 * 소셜 제공자별 토큰 검증 전략. 새 제공자는 이 인터페이스 구현체만 추가하면 된다(OCP).
 */
interface OAuthClient {
    val provider: OAuthProvider

    fun verify(idToken: String): OAuthUserInfo
}
