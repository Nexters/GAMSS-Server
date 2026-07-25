package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.LlmSettingsResponse
import com.nexters.gamss.admin.controller.dto.UpdateLlmSettingsRequest
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.llm.LlmSettingsService
import com.nexters.gamss.llm.PromptType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "백오피스 LLM 설정", description = "LLM 모델·시스템 프롬프트 조회·수정 (ROLE_ADMIN 필요). 재배포 없이 다음 생성부터 반영.")
@RestController
@RequestMapping("/api/admin/llm-settings")
class AdminLlmSettingsController(
    private val llmSettingsService: LlmSettingsService,
) {
    @Operation(summary = "LLM 설정 조회", description = "현재 모델·시스템 프롬프트와 선택 가능한 모델 목록을 반환합니다.")
    @GetMapping
    fun get(): ApiResponse<LlmSettingsResponse> =
        ApiResponse.success(
            LlmSettingsResponse.of(
                llmSettingsService.current(PromptType.COMMENT),
                llmSettingsService.defaults(PromptType.COMMENT),
                llmSettingsService.availableModels(),
            ),
        )

    @Operation(
        summary = "LLM 설정 수정",
        description =
            "모델·시스템 프롬프트를 갱신합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | model·systemPrompt 누락 또는 지원하지 않는 모델 |",
    )
    @PutMapping
    fun update(
        @Valid @RequestBody request: UpdateLlmSettingsRequest,
    ): ApiResponse<LlmSettingsResponse> {
        llmSettingsService.update(PromptType.COMMENT, request.model, request.systemPrompt)
        return ApiResponse.success(
            LlmSettingsResponse.of(
                llmSettingsService.current(PromptType.COMMENT),
                llmSettingsService.defaults(PromptType.COMMENT),
                llmSettingsService.availableModels(),
            ),
        )
    }
}
