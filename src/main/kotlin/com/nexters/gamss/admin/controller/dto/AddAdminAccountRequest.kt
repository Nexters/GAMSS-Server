package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AddAdminAccountRequest(
    @field:NotBlank(message = "email은 필수입니다.")
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    @field:Size(max = 255, message = "email은 255자 이하여야 합니다.")
    @field:Schema(description = "관리자로 추가할 이메일", example = "teammate@gmail.com")
    val email: String?,
)
