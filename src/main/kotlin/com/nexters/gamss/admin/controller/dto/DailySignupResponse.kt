package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.member.service.DailySignup
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class DailySignupResponse(
    @field:Schema(description = "날짜(KST)", example = "2026-07-21")
    val date: LocalDate,
    @field:Schema(description = "해당 날짜 가입 수", example = "3")
    val count: Long,
) {
    companion object {
        fun from(signup: DailySignup): DailySignupResponse = DailySignupResponse(signup.date, signup.count)
    }
}
