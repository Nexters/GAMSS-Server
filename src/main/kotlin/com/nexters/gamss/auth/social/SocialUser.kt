package com.nexters.gamss.auth.social

/**
 * 소셜 토큰 검증으로 추출한 사용자 식별 정보.
 * uid 는 제공자 안에서 유일한 식별자(Firebase UID)이고, provider 는 실제 로그인 수단이다.
 */
data class SocialUser(
    val uid: String,
    val provider: SocialProvider,
    val email: String?,
)
