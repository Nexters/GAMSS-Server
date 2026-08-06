package com.nexters.gamss.auth.service

/**
 * 로그인 결과. 재발급과 달리 최초 가입 여부(isFirstLogin)를 함께 담는다.
 */
data class LoginResult(
    val accessToken: String,
    val refreshToken: String,
    val isFirstLogin: Boolean,
)
