package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.PageResponse
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.member.controller.dto.MemberResponse
import com.nexters.gamss.member.service.MemberService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "백오피스 회원", description = "관리자용 회원 조회 API (ROLE_ADMIN 필요)")
@RestController
@RequestMapping("/api/admin/members")
class AdminMemberController(
    private val memberService: MemberService,
) {
    @Operation(
        summary = "회원 목록 조회",
        description = "가입 회원을 최신순으로 조회합니다. search 로 이메일·닉네임 부분 검색이 가능합니다.",
    )
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) search: String?,
    ): ApiResponse<PageResponse<MemberResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        val result = memberService.search(search, pageable)
        return ApiResponse.success(PageResponse.from(result, MemberResponse::from))
    }

    @Operation(summary = "회원 상세 조회")
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): ApiResponse<MemberResponse> = ApiResponse.success(MemberResponse.from(memberService.getById(id)))
}
