package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.llm.provider.LlmProvider
import io.swagger.v3.oas.annotations.media.Schema

/** 지금 쓰는 Gemini 호출 경로. 앱 전체 단일 설정이다. */
data class LlmProviderResponse(
    @field:Schema(description = "현재 호출 경로", example = "AI_STUDIO")
    val provider: LlmProvider,
    @field:Schema(description = "선택 가능한 호출 경로")
    val availableProviders: List<LlmProvider>,
)
