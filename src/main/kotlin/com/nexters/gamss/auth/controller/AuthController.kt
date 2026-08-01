package com.nexters.gamss.auth.controller

import com.nexters.gamss.auth.controller.dto.LoginRequest
import com.nexters.gamss.auth.controller.dto.ReissueRequest
import com.nexters.gamss.auth.controller.dto.TokenResponse
import com.nexters.gamss.auth.service.AuthService
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.AuthPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "인증", description = "소셜 로그인 · 토큰 재발급 · 로그아웃 API")
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
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | idToken 누락 |\n" +
                "| INVALID_SOCIAL_TOKEN | 401 | Firebase ID 토큰 무효(서명·발급자·만료 등) |\n" +
                "| UNSUPPORTED_SOCIAL_PROVIDER | 400 | 지원하지 않는 로그인 수단(구글·애플 외) |\n" +
                "| WITHDRAWN_MEMBER | 403 | 탈퇴한 회원 |",
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
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | refreshToken 누락 |\n" +
                "| EXPIRED_TOKEN | 401 | 만료된 refreshToken → 재로그인 필요 |\n" +
                "| INVALID_TOKEN | 401 | 유효하지 않거나 이미 회전된 refreshToken |\n" +
                "| REFRESH_TOKEN_NOT_FOUND | 401 | 저장된 refreshToken 없음 → 재로그인 필요 |\n" +
                "| WITHDRAWN_MEMBER | 403 | 탈퇴한 회원 |",
    )
    @PostMapping("/reissue")
    fun reissue(
        @Valid @RequestBody request: ReissueRequest,
    ): ApiResponse<TokenResponse> {
        val result = authService.reissue(request.refreshToken)
        return ApiResponse.success(TokenResponse.from(result))
    }

    @Operation(
        summary = "로그아웃",
        description =
            "서버에 저장된 refreshToken을 폐기합니다. 이후 그 refreshToken으로는 재발급되지 않습니다.\n\n" +
                "- 이미 로그아웃했거나 저장된 토큰이 없어도 **성공**합니다(멱등). 재시도해도 안전합니다.\n" +
                "- accessToken은 무상태라 서버에서 무효화할 수 없습니다. 남은 유효기간(기본 1시간) 동안은 " +
                "계속 통과하므로, **클라이언트가 보관 중인 토큰을 함께 삭제**해야 합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요 |",
    )
    @PostMapping("/logout")
    fun logout(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
    ): ApiResponse<Unit> {
        authService.logout(principal.memberId)
        return ApiResponse.success()
    }
}
