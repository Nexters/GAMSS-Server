package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.admin.service.DailyGeneration
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

/** 하루치 LLM 생성 성공/실패 수. 대시보드 품질 추이 차트의 데이터 포인트. */
data class DailyGenerationResponse(
    @field:Schema(description = "날짜(KST)", example = "2026-07-26")
    val date: LocalDate,
    @field:Schema(description = "그날 성공한 생성 수", example = "48")
    val success: Long,
    @field:Schema(description = "그날 실패한 생성 수", example = "2")
    val failed: Long,
) {
    companion object {
        fun from(generation: DailyGeneration): DailyGenerationResponse =
            DailyGenerationResponse(
                date = generation.date,
                success = generation.success,
                failed = generation.failed,
            )
    }
}
