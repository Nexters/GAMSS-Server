package com.nexters.gamss.auth.controller.dto

import com.nexters.gamss.auth.service.TokenResult
import io.swagger.v3.oas.annotations.media.Schema

data class TokenResponse(
    @field:Schema(description = "액세스 토큰 (Authorization 헤더에 Bearer로 사용)")
    val accessToken: String,
    @field:Schema(description = "리프레시 토큰 (재발급에 사용)")
    val refreshToken: String,
) {
    companion object {
        fun from(result: TokenResult): TokenResponse = TokenResponse(result.accessToken, result.refreshToken)
    }
}
