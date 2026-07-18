package com.nexters.gamss.auth.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class ReissueRequest(
    @field:NotBlank(message = "refreshToken은 필수입니다.")
    @field:Schema(description = "로그인 시 발급받은 refreshToken", example = "eyJhbGciOiJIUzI1NiJ9...")
    val refreshToken: String,
)
