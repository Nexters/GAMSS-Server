package com.nexters.gamss.auth.controller

import com.nexters.gamss.auth.controller.dto.LoginRequest
import com.nexters.gamss.auth.controller.dto.ReissueRequest
import com.nexters.gamss.auth.controller.dto.TokenResponse
import com.nexters.gamss.auth.oauth.OAuthProvider
import com.nexters.gamss.auth.service.AuthFacade
import com.nexters.gamss.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "인증", description = "소셜 로그인 및 토큰 재발급 API")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authFacade: AuthFacade,
) {
    @Operation(
        summary = "소셜 로그인",
        description =
            "앱이 소셜 SDK로 받은 id_token을 검증해 회원을 조회·가입하고 " +
                "서비스 토큰(accessToken·refreshToken)을 발급합니다. 최초 로그인 시 회원이 자동 생성됩니다.",
    )
    @PostMapping("/login/{provider}")
    fun login(
        @Parameter(description = "소셜 제공자 (google 또는 apple)", example = "google")
        @PathVariable provider: String,
        @Valid @RequestBody request: LoginRequest,
    ): ApiResponse<TokenResponse> {
        val result = authFacade.login(OAuthProvider.from(provider), request.idToken)
        return ApiResponse.success(TokenResponse.from(result))
    }

    @Operation(
        summary = "토큰 재발급",
        description =
            "refreshToken으로 accessToken·refreshToken을 재발급합니다. " +
                "refreshToken은 회전(rotate)되어 이전 토큰은 무효화됩니다.",
    )
    @PostMapping("/reissue")
    fun reissue(
        @Valid @RequestBody request: ReissueRequest,
    ): ApiResponse<TokenResponse> {
        val result = authFacade.reissue(request.refreshToken)
        return ApiResponse.success(TokenResponse.from(result))
    }
}
