package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.config.AdminProperties
import com.nexters.gamss.admin.controller.dto.AdminDevLoginRequest
import com.nexters.gamss.admin.controller.dto.AdminTokenResponse
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.JwtIssuer
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 로컬 개발 전용 관리자 로그인. **local 프로필에서만 빈으로 등록**되므로 dev/prod에는 이 엔드포인트가
 * 아예 존재하지 않는다. Firebase 없이 허용목록 이메일만으로 관리자 토큰을 발급해 로컬 개발을 돕는다.
 */
@Profile("local")
@Tag(name = "백오피스 인증(로컬 전용)", description = "Firebase 없이 로그인하는 로컬 개발용 API")
@RestController
@RequestMapping("/api/admin/auth")
class AdminDevAuthController(
    private val adminProperties: AdminProperties,
    private val jwtIssuer: JwtIssuer,
) {
    @Operation(
        summary = "로컬 개발용 관리자 로그인",
        description = "허용목록에 있는 이메일이면 Firebase 검증 없이 관리자 토큰을 발급합니다. local 프로필 전용입니다.",
    )
    @PostMapping("/dev-login")
    fun devLogin(
        @Valid @RequestBody request: AdminDevLoginRequest,
    ): ApiResponse<AdminTokenResponse> {
        if (!adminProperties.isAllowed(request.email)) {
            throw BusinessException(ErrorCode.NOT_ADMIN)
        }
        return ApiResponse.success(AdminTokenResponse(jwtIssuer.issueAdminToken(request.email.trim().lowercase())))
    }
}
