package com.nexters.gamss.member.controller

import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.AuthPrincipal
import com.nexters.gamss.member.controller.dto.MemberResponse
import com.nexters.gamss.member.controller.dto.TokenUsageResponse
import com.nexters.gamss.member.controller.dto.UpdateNicknameRequest
import com.nexters.gamss.member.domain.Nickname
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.tokenlimit.service.DailyTokenLimitService
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
    private val dailyTokenLimitService: DailyTokenLimitService,
) {
    @Operation(
        summary = "내 정보 조회",
        description =
            "로그인한 회원의 정보(이메일·이름·닉네임·가입일·상태)를 반환합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |",
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
            "닉네임을 변경합니다. 앞뒤 공백은 제거되며 ${Nickname.MIN_LENGTH}~${Nickname.MAX_LENGTH}자·" +
                "금칙어 규칙을 따릅니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| INVALID_INPUT | 400 | nickname 누락 |\n" +
                "| INVALID_NICKNAME | 400 | 길이(${Nickname.MIN_LENGTH}~${Nickname.MAX_LENGTH}자) 위반 또는 금칙어 포함 |",
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
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| ALREADY_WITHDRAWN | 409 | 이미 탈퇴한 회원 |",
    )
    @DeleteMapping("/me")
    fun withdraw(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
    ): ApiResponse<Unit> {
        memberService.withdraw(principal.memberId)
        return ApiResponse.success()
    }

    @Operation(
        summary = "내 오늘 토큰 사용량과 유료 잔액 조회",
        description =
            "로그인한 회원 본인의 오늘(KST, 정책 리셋 시각 기준) 토큰 사용량, 일일 상한, 남은 유료 토큰(paidBalance)을 반환합니다. " +
                "일일 토큰을 다 쓴 뒤의 댓글·답글 생성은 유료 토큰에서 차감되고, 그 몫도 usedTokens에 들어가므로 " +
                "usedTokens가 dailyLimit보다 클 수 있습니다. " +
                "exceeded는 댓글·답글 생성이 막혔는지를 뜻하며, 일일 토큰과 유료 잔액이 모두 " +
                "소진(paidBalance <= 0)됐을 때만 true입니다. " +
                "paidBalance는 리셋과 상관없는 누적 잔액이고, 환불 회수로 음수일 수 있습니다. " +
                "상한이 적용되지 않는 환경(local 등)에서는 dailyLimit이 null(무제한), exceeded가 false로 내려가고, " +
                "paidBalance는 그대로 실제 잔액이 내려갑니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |",
    )
    @GetMapping("/me/token-usage")
    fun tokenUsage(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
    ): ApiResponse<TokenUsageResponse> {
        val usage = dailyTokenLimitService.usageFor(principal.memberId)
        return ApiResponse.success(
            TokenUsageResponse(
                usedTokens = usage.usedTokens,
                dailyLimit = usage.dailyLimit,
                exceeded = usage.exceeded,
                paidBalance = usage.paidBalance,
            ),
        )
    }
}
