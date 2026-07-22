package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

/**
 * 로컬 개발 전용 관리자 로그인 요청. Firebase 없이 허용목록 이메일로 토큰을 발급받는다.
 */
data class AdminDevLoginRequest(
    @field:NotBlank
    @field:Schema(description = "허용목록에 있는 관리자 이메일", example = "admin@gamss.kr")
    val email: String,
)
