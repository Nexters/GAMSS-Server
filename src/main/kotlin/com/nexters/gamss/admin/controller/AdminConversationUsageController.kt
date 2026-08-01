package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.ConversationUsageResponse
import com.nexters.gamss.admin.service.ConversationUsageService
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.response.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "백오피스 대화방 사용량",
    description = "모든 대화방의 토큰 사용량 조회 (ROLE_ADMIN 필요). prod·dev 구분 없이 대화방별 메시지 수·카드 여부·소비 토큰을 최신순으로 본다.",
)
@Validated
@RestController
@RequestMapping("/api/admin/conversation-usage")
class AdminConversationUsageController(
    private val conversationUsageService: ConversationUsageService,
) {
    @Operation(
        summary = "대화방별 사용량 목록",
        description =
            "대화방을 최신순으로 페이지네이션해 각 대화방의 유저·캐릭터 메시지 수, 카드 생성 여부, 소비 토큰(총량·캐시)을 반환합니다. " +
                "이 배포 이전(대화방 귀속 정보가 없던 시기)의 생성 로그 토큰은 어느 대화방에도 집계되지 않습니다.",
    )
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
    ): ApiResponse<PageResponse<ConversationUsageResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        val result = conversationUsageService.getUsage(pageable)
        return ApiResponse.success(PageResponse.from(result, ConversationUsageResponse::from))
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100L
    }
}
