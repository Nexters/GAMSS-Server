package com.nexters.gamss.auth.controller

import com.nexters.gamss.auth.controller.dto.LoginRequest
import com.nexters.gamss.auth.controller.dto.ReissueRequest
import com.nexters.gamss.auth.controller.dto.TokenResponse
import com.nexters.gamss.auth.service.AuthService
import com.nexters.gamss.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "인증", description = "소셜 로그인 및 토큰 재발급 API")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {
    @Operation(
        summary = "소셜 로그인",
        description =
            "앱이 Firebase Authentication으로 발급받은 ID 토큰을 검증해 회원을 조회·가입하고 " +
                "서비스 토큰(accessToken·refreshToken)을 발급합니다. 최초 로그인 시 회원이 자동 생성됩니다. " +
                "로그인 수단(구글·애플)은 토큰에서 판별하므로 별도로 지정하지 않습니다.\n\n" +
                "**실패 응답(error.code):**\n" +
                "- `INVALID_INPUT` (400): idToken 누락\n" +
                "- `INVALID_SOCIAL_TOKEN` (401): Firebase ID 토큰이 유효하지 않음(서명·발급자·만료 등)\n" +
                "- `UNSUPPORTED_SOCIAL_PROVIDER` (400): 지원하지 않는 로그인 수단(구글·애플 외)\n" +
                "- `WITHDRAWN_MEMBER` (403): 탈퇴한 회원",
    )
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ApiResponse<TokenResponse> {
        val result = authService.login(request.idToken)
        return ApiResponse.success(TokenResponse.from(result))
    }

    @Operation(
        summary = "토큰 재발급",
        description =
            "refreshToken으로 accessToken·refreshToken을 재발급합니다. " +
                "refreshToken은 회전(rotate)되어 이전 토큰은 무효화됩니다.\n\n" +
                "**실패 응답(error.code):**\n" +
                "- `INVALID_INPUT` (400): refreshToken 누락\n" +
                "- `EXPIRED_TOKEN` (401): 만료된 refreshToken → 재로그인 필요\n" +
                "- `INVALID_TOKEN` (401): 유효하지 않거나 이미 회전된 refreshToken\n" +
                "- `REFRESH_TOKEN_NOT_FOUND` (401): 저장된 refreshToken 없음 → 재로그인 필요\n" +
                "- `WITHDRAWN_MEMBER` (403): 탈퇴한 회원",
    )
    @PostMapping("/reissue")
    fun reissue(
        @Valid @RequestBody request: ReissueRequest,
    ): ApiResponse<TokenResponse> {
        val result = authService.reissue(request.refreshToken)
        return ApiResponse.success(TokenResponse.from(result))
    }
}
