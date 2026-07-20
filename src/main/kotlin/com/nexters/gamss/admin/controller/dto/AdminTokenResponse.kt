package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

data class AdminTokenResponse(
    @field:Schema(description = "백오피스 관리자 액세스 토큰. 이후 요청의 Authorization: Bearer 에 싣는다.")
    val accessToken: String,
)
