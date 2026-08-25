package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.MemberStatsResponse
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.response.PageResponse
import com.nexters.gamss.member.controller.dto.MemberResponse
import com.nexters.gamss.member.domain.MemberStatus
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.member.service.MemberStatsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "백오피스 회원", description = "관리자용 회원 조회·관리 API (ROLE_ADMIN 필요)")
@Validated
@RestController
@RequestMapping("/api/admin/members")
class AdminMemberController(
    private val memberService: MemberService,
    private val memberStatsService: MemberStatsService,
) {
    @Operation(
        summary = "회원 목록 조회",
        description = "가입 회원을 최신순으로 조회합니다. search 로 이메일·이름·닉네임 부분 검색, status 로 상태 필터가 가능합니다.",
    )
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) status: MemberStatus?,
    ): ApiResponse<PageResponse<MemberResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        val result = memberService.search(search, status, pageable)
        return ApiResponse.success(PageResponse.from(result, MemberResponse::from))
    }

    @Operation(
        summary = "회원 통계",
        description = "대시보드용 집계: 전체·활성·탈퇴 회원 수와 최근 days 일간 일자별 가입 추이(KST).",
    )
    @GetMapping("/stats")
    fun stats(
        @RequestParam(defaultValue = "14") @Min(1) @Max(MAX_STATS_DAYS) days: Int,
    ): ApiResponse<MemberStatsResponse> = ApiResponse.success(MemberStatsResponse.from(memberStatsService.getStats(days)))

    @Operation(summary = "회원 상세 조회")
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): ApiResponse<MemberResponse> = ApiResponse.success(MemberResponse.from(memberService.getById(id)))

    @Operation(
        summary = "회원 강제 탈퇴",
        description =
            "관리자가 회원을 탈퇴 처리합니다. 회원 본인 탈퇴와 같은 처리로, 통계를 위해 회원 행은 남기되 " +
                "이메일·닉네임 등 개인 식별정보는 비우고 소셜 계정 연결·리프레시 토큰은 삭제합니다. " +
                "해당 소셜 계정으로 다시 로그인하면 이전 기록과 분리된 신규 회원으로 가입됩니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| MEMBER_NOT_FOUND | 404 | 존재하지 않는 회원 |\n" +
                "| ALREADY_WITHDRAWN | 409 | 이미 탈퇴한 회원 |",
    )
    @PostMapping("/{id}/withdraw")
    fun withdraw(
        @PathVariable id: Long,
    ): ApiResponse<MemberResponse> {
        memberService.withdraw(id)
        return ApiResponse.success(MemberResponse.from(memberService.getById(id)))
    }

    companion object {
        // 한 번에 대량 로우를 끌어오지 못하도록 목록·통계 파라미터에 상한을 둔다.
        private const val MAX_PAGE_SIZE = 100L
        private const val MAX_STATS_DAYS = 365L
    }
}
