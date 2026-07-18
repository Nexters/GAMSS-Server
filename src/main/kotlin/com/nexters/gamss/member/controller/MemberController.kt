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
        description = "로그인한 회원의 정보(이메일·닉네임·가입일·상태)를 반환합니다.",
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
        description = "닉네임을 변경합니다. 앞뒤 공백은 제거되며 2~20자·금칙어 규칙을 따릅니다.",
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
        description = "소프트 삭제로 탈퇴 처리합니다. 탈퇴 후에는 로그인·토큰 재발급이 차단됩니다.",
    )
    @DeleteMapping("/me")
    fun withdraw(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
    ): ApiResponse<Unit> {
        memberService.withdraw(principal.memberId)
        return ApiResponse.success()
    }
}
