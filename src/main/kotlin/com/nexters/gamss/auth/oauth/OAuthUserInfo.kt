package com.nexters.gamss.auth.oauth

/**
 * 소셜 토큰 검증으로 추출한 사용자 식별 정보.
 */
data class OAuthUserInfo(
    val providerId: String,
    val email: String?,
)
