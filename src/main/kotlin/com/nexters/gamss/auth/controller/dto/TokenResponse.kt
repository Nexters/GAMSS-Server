package com.nexters.gamss.auth.controller.dto

import com.nexters.gamss.auth.service.TokenResult

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
) {
    companion object {
        fun from(result: TokenResult): TokenResponse = TokenResponse(result.accessToken, result.refreshToken)
    }
}
