package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.QualityStatsResponse
import com.nexters.gamss.admin.controller.dto.UsageStatsResponse
import com.nexters.gamss.admin.service.QualityStatsService
import com.nexters.gamss.admin.service.UsageStatsService
import com.nexters.gamss.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "백오피스 대시보드", description = "관리자용 사용량·LLM 품질 모니터링 지표 API (ROLE_ADMIN 필요)")
@Validated
@RestController
@RequestMapping("/api/admin/dashboard")
class AdminDashboardController(
    private val usageStatsService: UsageStatsService,
    private val qualityStatsService: QualityStatsService,
) {
    @Operation(
        summary = "사용량 지표",
        description = "오늘 대화·메시지·카드·가입 수, DAU/WAU, 감정 분포, 최근 days 일간 활동 추이(KST).",
    )
    @GetMapping("/usage")
    fun usage(
        @RequestParam(defaultValue = "14") @Min(1) @Max(MAX_DAYS) days: Int,
    ): ApiResponse<UsageStatsResponse> = ApiResponse.success(UsageStatsResponse.from(usageStatsService.getUsageStats(days)))

    @Operation(
        summary = "LLM 품질·안정성 지표",
        description = "최근 days 일간 생성 성공률·호출 수·재시도율·지연(avg/p95)·토큰 사용량과 막힌 PENDING 수.",
    )
    @GetMapping("/quality")
    fun quality(
        @RequestParam(defaultValue = "14") @Min(1) @Max(MAX_DAYS) days: Int,
    ): ApiResponse<QualityStatsResponse> = ApiResponse.success(QualityStatsResponse.from(qualityStatsService.getQualityStats(days)))

    companion object {
        private const val MAX_DAYS = 90L
    }
}
