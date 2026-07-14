package com.nexters.gamss.auth.controller.dto

import com.nexters.gamss.auth.service.TokenResult
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "idToken은 필수입니다.")
    val idToken: String,
)

data class ReissueRequest(
    @field:NotBlank(message = "refreshToken은 필수입니다.")
    val refreshToken: String,
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
) {
    companion object {
        fun from(result: TokenResult): TokenResponse = TokenResponse(result.accessToken, result.refreshToken)
    }
}
