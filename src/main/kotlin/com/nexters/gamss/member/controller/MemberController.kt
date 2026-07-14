package com.nexters.gamss.member.controller

import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.AuthPrincipal
import com.nexters.gamss.member.controller.dto.MemberResponse
import com.nexters.gamss.member.service.MemberService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/members")
class MemberController(
    private val memberService: MemberService,
) {
    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ApiResponse<MemberResponse> {
        val member = memberService.getById(principal.memberId)
        return ApiResponse.success(MemberResponse.from(member))
    }
}
