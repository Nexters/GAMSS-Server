package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.LlmSettingsResponse
import com.nexters.gamss.admin.controller.dto.UpdateLlmSettingsRequest
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.llm.LlmSettingsService
import com.nexters.gamss.llm.PromptType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "백오피스 LLM 설정",
    description =
        "LLM 모델·시스템 프롬프트 조회·수정 (ROLE_ADMIN 필요). 재배포 없이 다음 생성부터 반영. " +
            "실제 시스템 프롬프트는 COMMON(공통) + 타입(COMMENT/REPLY/CARD)으로 조립된다.",
)
@RestController
@RequestMapping("/api/admin/llm-settings")
class AdminLlmSettingsController(
    private val llmSettingsService: LlmSettingsService,
) {
    @Operation(
        summary = "LLM 설정 조회",
        description = "타입별 모델·시스템 프롬프트와 선택 가능한 모델 목록을 반환합니다. promptType 생략 시 COMMENT.",
    )
    @GetMapping
    fun get(
        @Parameter(description = "조회할 프롬프트 타입", example = "COMMENT")
        @RequestParam(defaultValue = "COMMENT") promptType: PromptType,
    ): ApiResponse<LlmSettingsResponse> = ApiResponse.success(response(promptType))

    @Operation(
        summary = "LLM 설정 수정",
        description =
            "타입별 모델·시스템 프롬프트를 갱신합니다. COMMON 타입은 모델 없이 프롬프트만 갱신합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | promptType·systemPrompt 누락, 또는 model 누락·지원하지 않는 모델(COMMON 제외) |",
    )
    @PutMapping
    fun update(
        @Valid @RequestBody request: UpdateLlmSettingsRequest,
    ): ApiResponse<LlmSettingsResponse> {
        val promptType = checkNotNull(request.promptType)
        llmSettingsService.update(promptType, request.model, request.systemPrompt)
        return ApiResponse.success(response(promptType))
    }

    private fun response(promptType: PromptType): LlmSettingsResponse =
        LlmSettingsResponse.of(
            promptType,
            llmSettingsService.current(promptType),
            llmSettingsService.defaults(promptType),
            llmSettingsService.availableModels(),
        )
}
