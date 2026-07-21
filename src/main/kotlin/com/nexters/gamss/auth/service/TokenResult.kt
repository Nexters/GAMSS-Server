package com.nexters.gamss.auth.service

data class TokenResult(
    val accessToken: String,
    val refreshToken: String,
)
