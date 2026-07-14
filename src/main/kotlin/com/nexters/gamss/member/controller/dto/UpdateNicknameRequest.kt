package com.nexters.gamss.member.controller.dto

import jakarta.validation.constraints.NotBlank

data class UpdateNicknameRequest(
    @field:NotBlank(message = "nickname은 필수입니다.")
    val nickname: String,
)
