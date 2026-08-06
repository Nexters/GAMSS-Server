package com.nexters.gamss.auth.controller.dto

import com.nexters.gamss.auth.service.LoginResult
import io.swagger.v3.oas.annotations.media.Schema

data class LoginResponse(
    @field:Schema(description = "액세스 토큰 (Authorization 헤더에 Bearer로 사용)")
    val accessToken: String,
    @field:Schema(description = "리프레시 토큰 (재발급에 사용)")
    val refreshToken: String,
    @field:Schema(description = "이번 로그인으로 새로 가입했으면 true, 기존 회원이면 false")
    val isFirstLogin: Boolean,
) {
    companion object {
        fun from(result: LoginResult): LoginResponse =
            LoginResponse(result.accessToken, result.refreshToken, result.isFirstLogin)
    }
}
