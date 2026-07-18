package com.nexters.gamss.member.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class UpdateNicknameRequest(
    @field:NotBlank(message = "nickname은 필수입니다.")
    @field:Schema(description = "새 닉네임 (앞뒤 공백 제거 후 2~20자, 금칙어 불가)", example = "바다")
    val nickname: String,
)
