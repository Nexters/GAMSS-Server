package com.nexters.gamss.global.response

import io.swagger.v3.oas.annotations.media.Schema

data class ErrorResponse(
    @field:Schema(description = "에러 코드. 클라이언트 분기용 식별자다.", example = "INVALID_SOCIAL_TOKEN")
    val code: String,
    @field:Schema(description = "사람이 읽을 수 있는 에러 메시지", example = "유효하지 않은 소셜 토큰입니다.")
    val message: String,
)
