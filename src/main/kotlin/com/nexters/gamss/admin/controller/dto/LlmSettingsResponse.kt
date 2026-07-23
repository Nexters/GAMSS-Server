package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.llm.LlmSettingsView
import io.swagger.v3.oas.annotations.media.Schema

data class LlmSettingsResponse(
    @field:Schema(description = "현재 적용 중인 모델", example = "gemini-3.1-flash-lite")
    val model: String,
    @field:Schema(description = "현재 적용 중인 시스템 프롬프트")
    val systemPrompt: String,
    @field:Schema(description = "선택 가능한 모델 목록(Gemini API에서 동적 조회)")
    val availableModels: List<String>,
    @field:Schema(description = "코드 기본값 모델(기본값으로 복원용)")
    val defaultModel: String,
    @field:Schema(description = "코드 기본값 시스템 프롬프트(기본값으로 복원용)")
    val defaultSystemPrompt: String,
) {
    companion object {
        fun of(
            current: LlmSettingsView,
            defaults: LlmSettingsView,
            availableModels: List<String>,
        ): LlmSettingsResponse =
            LlmSettingsResponse(
                model = current.model,
                systemPrompt = current.systemPrompt,
                availableModels = availableModels,
                defaultModel = defaults.model,
                defaultSystemPrompt = defaults.systemPrompt,
            )
    }
}
