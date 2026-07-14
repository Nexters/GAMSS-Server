package com.nexters.gamss.auth.controller.dto

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "idToken은 필수입니다.")
    val idToken: String,
)
