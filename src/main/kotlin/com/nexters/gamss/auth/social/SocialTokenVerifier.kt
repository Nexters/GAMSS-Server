package com.nexters.gamss.auth.social

/**
 * 소셜 로그인 토큰을 검증하고 사용자 식별 정보를 추출한다. 구현(Firebase 등)은 교체 가능하다.
 */
interface SocialTokenVerifier {
    fun verify(idToken: String): SocialUser
}
