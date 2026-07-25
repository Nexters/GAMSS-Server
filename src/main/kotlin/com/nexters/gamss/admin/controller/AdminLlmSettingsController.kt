package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.ModelSettingResponse
import com.nexters.gamss.admin.controller.dto.PromptSettingResponse
import com.nexters.gamss.admin.controller.dto.UpdateModelRequest
import com.nexters.gamss.admin.controller.dto.UpdatePromptRequest
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.llm.settings.LlmSettingsService
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
            "모델은 앱 전체 단일 설정이고, 프롬프트는 타입별(COMMON 공통 + COMMENT/REPLY/CARD)로 조립된다.",
)
@RestController
@RequestMapping("/api/admin/llm-settings")
class AdminLlmSettingsController(
    private val llmSettingsService: LlmSettingsService,
) {
    @Operation(summary = "모델 조회", description = "앱 전체 단일 모델과 선택 가능한 모델 목록을 반환합니다.")
    @GetMapping("/model")
    fun getModel(): ApiResponse<ModelSettingResponse> = ApiResponse.success(modelResponse())

    @Operation(
        summary = "모델 수정",
        description =
            "앱 전체 모델을 갱신합니다(댓글·답글·카드 공통).\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | model 누락 또는 지원하지 않는 모델 |",
    )
    @PutMapping("/model")
    fun updateModel(
        @Valid @RequestBody request: UpdateModelRequest,
    ): ApiResponse<ModelSettingResponse> {
        llmSettingsService.updateModel(checkNotNull(request.model))
        return ApiResponse.success(modelResponse())
    }

    @Operation(
        summary = "프롬프트 조회",
        description = "타입별 프롬프트 원본과 코드 기본값을 반환합니다. promptType 생략 시 COMMENT.",
    )
    @GetMapping("/prompt")
    fun getPrompt(
        @Parameter(description = "조회할 프롬프트 타입", example = "COMMENT")
        @RequestParam(defaultValue = "COMMENT") promptType: PromptType,
    ): ApiResponse<PromptSettingResponse> = ApiResponse.success(promptResponse(promptType))

    @Operation(
        summary = "프롬프트 수정",
        description =
            "타입별 시스템 프롬프트를 갱신합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | promptType·systemPrompt 누락 |",
    )
    @PutMapping("/prompt")
    fun updatePrompt(
        @Valid @RequestBody request: UpdatePromptRequest,
    ): ApiResponse<PromptSettingResponse> {
        val promptType = checkNotNull(request.promptType)
        llmSettingsService.updatePrompt(promptType, request.systemPrompt)
        return ApiResponse.success(promptResponse(promptType))
    }

    private fun modelResponse(): ModelSettingResponse =
        ModelSettingResponse(
            model = llmSettingsService.currentModel(),
            availableModels = llmSettingsService.availableModels(),
            defaultModel = llmSettingsService.defaultModel(),
        )

    private fun promptResponse(promptType: PromptType): PromptSettingResponse =
        PromptSettingResponse(
            promptType = promptType.name,
            systemPrompt = llmSettingsService.currentPrompt(promptType),
            defaultSystemPrompt = llmSettingsService.defaultPrompt(promptType),
        )
}
