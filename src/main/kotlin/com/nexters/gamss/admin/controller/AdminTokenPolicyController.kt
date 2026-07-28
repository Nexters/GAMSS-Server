package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.TokenPolicyResponse
import com.nexters.gamss.admin.controller.dto.UpdateTokenPolicyRequest
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.tokenlimit.service.TokenPolicyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "백오피스 토큰 상한",
    description =
        "유저별 일일 토큰 상한 정책 조회·수정 (ROLE_ADMIN 필요). 값·리셋 시각은 여기서 조절하며 다음 생성부터 반영된다. " +
            "상한의 실제 적용은 prod 환경에서만 이뤄지고(dev는 미적용), 초과 시 메시지 저장은 유지된 채 생성만 차단된다.",
)
@RestController
@RequestMapping("/api/admin/token-policy")
class AdminTokenPolicyController(
    private val tokenPolicyService: TokenPolicyService,
) {
    @Operation(summary = "일일 토큰 상한 조회", description = "현재 상한 값과 리셋 시각(KST)을 반환합니다.")
    @GetMapping
    fun getPolicy(): ApiResponse<TokenPolicyResponse> = ApiResponse.success(TokenPolicyResponse.from(tokenPolicyService.current()))

    @Operation(
        summary = "일일 토큰 상한 수정",
        description =
            "유저별 일일 토큰 상한과 리셋 시각(KST)을 갱신합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | dailyTokenLimit·resetHour 누락 또는 범위 오류(리셋 0~23) |",
    )
    @PutMapping
    fun updatePolicy(
        @Valid @RequestBody request: UpdateTokenPolicyRequest,
    ): ApiResponse<TokenPolicyResponse> {
        val updated = tokenPolicyService.update(checkNotNull(request.dailyTokenLimit), checkNotNull(request.resetHour))
        return ApiResponse.success(TokenPolicyResponse.from(updated))
    }
}
