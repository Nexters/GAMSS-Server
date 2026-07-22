package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class AdminLoginRequest(
    @field:NotBlank
    @field:Schema(description = "구글 로그인으로 발급받은 Firebase ID 토큰")
    val idToken: String,
)
