package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

data class AdminMeResponse(
    @field:Schema(description = "로그인한 관리자 이메일", example = "admin@gamss.kr")
    val email: String,
)
