package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.AdminLoginRequest
import com.nexters.gamss.admin.controller.dto.AdminMeResponse
import com.nexters.gamss.admin.controller.dto.AdminTokenResponse
import com.nexters.gamss.admin.service.AdminAuthService
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.AdminPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "백오피스 인증", description = "관리자 구글 로그인 API")
@RestController
@RequestMapping("/api/admin/auth")
class AdminAuthController(
    private val adminAuthService: AdminAuthService,
) {
    @Operation(
        summary = "관리자 로그인",
        description =
            "구글 로그인으로 발급받은 Firebase ID 토큰을 검증하고, 이메일이 관리자 허용목록에 있으면 " +
                "백오피스 관리자 토큰을 발급합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | idToken 누락 |\n" +
                "| INVALID_SOCIAL_TOKEN | 401 | Firebase ID 토큰 무효 |\n" +
                "| NOT_ADMIN | 403 | 허용목록에 없는 이메일 |",
    )
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: AdminLoginRequest,
    ): ApiResponse<AdminTokenResponse> {
        val token = adminAuthService.login(request.idToken)
        return ApiResponse.success(AdminTokenResponse(token))
    }

    @Operation(
        summary = "현재 관리자 조회",
        description = "관리자 토큰이 유효한지 확인하고 로그인한 관리자 이메일을 돌려줍니다. 세션 확인용입니다.",
    )
    @GetMapping("/me")
    fun me(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AdminPrincipal,
    ): ApiResponse<AdminMeResponse> = ApiResponse.success(AdminMeResponse(principal.email))
}
