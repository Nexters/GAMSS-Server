package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.member.service.MemberStats
import io.swagger.v3.oas.annotations.media.Schema

data class MemberStatsResponse(
    @field:Schema(description = "전체 회원 수", example = "137")
    val total: Long,
    @field:Schema(description = "활성 회원 수", example = "120")
    val active: Long,
    @field:Schema(description = "탈퇴 회원 수", example = "17")
    val withdrawn: Long,
    @field:Schema(description = "최근 가입 추이(날짜 오름차순, 빈 날은 0)")
    val dailySignups: List<DailySignupResponse>,
) {
    companion object {
        fun from(stats: MemberStats): MemberStatsResponse =
            MemberStatsResponse(
                total = stats.total,
                active = stats.active,
                withdrawn = stats.withdrawn,
                dailySignups = stats.dailySignups.map(DailySignupResponse::from),
            )
    }
}
