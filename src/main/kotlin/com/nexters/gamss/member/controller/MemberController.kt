package com.nexters.gamss.member.controller

import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.AuthPrincipal
import com.nexters.gamss.member.controller.dto.MemberResponse
import com.nexters.gamss.member.controller.dto.UpdateNicknameRequest
import com.nexters.gamss.member.domain.Nickname
import com.nexters.gamss.member.service.MemberService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "회원", description = "내 프로필 조회·수정·탈퇴 API (모두 로그인 필요)")
@RestController
@RequestMapping("/api/members")
class MemberController(
    private val memberService: MemberService,
) {
    @Operation(
        summary = "내 정보 조회",
        description =
            "로그인한 회원의 정보(이메일·닉네임·가입일·상태)를 반환합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·만료·무효) |",
    )
    @GetMapping("/me")
    fun me(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
    ): ApiResponse<MemberResponse> {
        val member = memberService.getById(principal.memberId)
        return ApiResponse.success(MemberResponse.from(member))
    }

    @Operation(
        summary = "닉네임 수정",
        description =
            "닉네임을 변경합니다. 앞뒤 공백은 제거되며 2~20자·금칙어 규칙을 따릅니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요 |\n" +
                "| INVALID_INPUT | 400 | nickname 누락 |\n" +
                "| INVALID_NICKNAME | 400 | 길이(2~20자) 위반 또는 금칙어 포함 |",
    )
    @PatchMapping("/me/nickname")
    fun updateNickname(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Valid @RequestBody request: UpdateNicknameRequest,
    ): ApiResponse<MemberResponse> {
        val member = memberService.updateNickname(principal.memberId, Nickname(request.nickname))
        return ApiResponse.success(MemberResponse.from(member))
    }

    @Operation(
        summary = "회원 탈퇴",
        description =
            "탈퇴 처리합니다. 가입·탈퇴 통계를 위해 회원 행은 남기되 이메일·닉네임 등 개인 식별정보는 " +
                "비우고, 소셜 계정 연결과 리프레시 토큰은 삭제합니다.\n\n" +
                "- 이미 발급된 accessToken은 만료(기본 1시간)까지 유효하고, refreshToken 재발급은 즉시 차단됩니다.\n" +
                "- 같은 소셜 계정으로 다시 로그인하면 **이전 기록과 분리된 신규 회원**으로 가입됩니다 " +
                "(이전 대화·카드에는 접근할 수 없습니다).\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요 |\n" +
                "| ALREADY_WITHDRAWN | 409 | 이미 탈퇴한 회원 |",
    )
    @DeleteMapping("/me")
    fun withdraw(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
    ): ApiResponse<Unit> {
        memberService.withdraw(principal.memberId)
        return ApiResponse.success()
    }
}
