package com.nexters.gamss.auth.controller.dto

import jakarta.validation.constraints.NotBlank

data class ReissueRequest(
    @field:NotBlank(message = "refreshToken은 필수입니다.")
    val refreshToken: String,
)
