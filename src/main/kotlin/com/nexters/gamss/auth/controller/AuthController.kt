package com.nexters.gamss.auth.controller

import com.nexters.gamss.auth.controller.dto.LoginRequest
import com.nexters.gamss.auth.controller.dto.ReissueRequest
import com.nexters.gamss.auth.controller.dto.TokenResponse
import com.nexters.gamss.auth.oauth.OAuthProvider
import com.nexters.gamss.auth.service.AuthService
import com.nexters.gamss.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/login/{provider}")
    fun login(
        @PathVariable provider: String,
        @Valid @RequestBody request: LoginRequest,
    ): ApiResponse<TokenResponse> {
        val result = authService.login(OAuthProvider.from(provider), request.idToken)
        return ApiResponse.success(TokenResponse.from(result))
    }

    @PostMapping("/reissue")
    fun reissue(
        @Valid @RequestBody request: ReissueRequest,
    ): ApiResponse<TokenResponse> {
        val result = authService.reissue(request.refreshToken)
        return ApiResponse.success(TokenResponse.from(result))
    }
}
