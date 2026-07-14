package com.nexters.gamss.auth.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "idToken은 필수입니다.")
    @field:Schema(description = "소셜 SDK로 발급받은 idToken(JWT)", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6...")
    val idToken: String,
)
