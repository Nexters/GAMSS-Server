package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.ModelSettingResponse
import com.nexters.gamss.admin.controller.dto.PromptRevisionDetailResponse
import com.nexters.gamss.admin.controller.dto.PromptRevisionResponse
import com.nexters.gamss.admin.controller.dto.PromptSettingResponse
import com.nexters.gamss.admin.controller.dto.UpdateModelRequest
import com.nexters.gamss.admin.controller.dto.UpdatePromptRequest
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.response.PageResponse
import com.nexters.gamss.global.retry.ConflictRetry
import com.nexters.gamss.global.security.AdminPrincipal
import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.llm.settings.LlmSettingsService
import com.nexters.gamss.llm.settings.PromptRevisionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
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
@Validated
@RestController
@RequestMapping("/api/admin/llm-settings")
class AdminLlmSettingsController(
    private val llmSettingsService: LlmSettingsService,
    private val promptRevisionService: PromptRevisionService,
    private val conflictRetry: ConflictRetry,
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
            "타입별 시스템 프롬프트를 갱신하고 리비전(누가·언제·무엇)을 남깁니다. " +
                "내용이 현재값과 같으면 리비전 없이 성공합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | promptType·systemPrompt 누락 |",
    )
    @PutMapping("/prompt")
    fun updatePrompt(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AdminPrincipal,
        @Valid @RequestBody request: UpdatePromptRequest,
    ): ApiResponse<PromptSettingResponse> {
        val promptType = checkNotNull(request.promptType)
        // 최초 기록의 채번 경합은 트랜잭션 바깥에서 작업 전체를 재시도해 해소한다.
        conflictRetry.execute { promptRevisionService.savePrompt(promptType, request.systemPrompt, principal.email) }
        return ApiResponse.success(promptResponse(promptType))
    }

    @Operation(
        summary = "프롬프트 리비전 목록",
        description = "타입별 편집 이력을 최신 버전부터 반환합니다. 본문은 미리보기만 담기며 전체는 상세 조회로 받습니다.",
    )
    @GetMapping("/prompt/revisions")
    fun getRevisions(
        @Parameter(description = "조회할 프롬프트 타입", example = "COMMENT")
        @RequestParam promptType: PromptType,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "5") @Min(1) @Max(50) size: Int,
    ): ApiResponse<PageResponse<PromptRevisionResponse>> {
        val revisions = promptRevisionService.getRevisions(promptType, PageRequest.of(page, size))
        return ApiResponse.success(PageResponse.from(revisions, PromptRevisionResponse::from))
    }

    @Operation(
        summary = "프롬프트 리비전 상세",
        description =
            "리비전 한 건의 프롬프트 전문을 반환합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| PROMPT_REVISION_NOT_FOUND | 404 | 존재하지 않는 리비전 |",
    )
    @GetMapping("/prompt/revisions/{revisionId}")
    fun getRevision(
        @PathVariable revisionId: Long,
    ): ApiResponse<PromptRevisionDetailResponse> =
        ApiResponse.success(PromptRevisionDetailResponse.from(promptRevisionService.getRevision(revisionId)))

    @Operation(
        summary = "프롬프트 리비전 복원",
        description =
            "리비전 내용을 현재 프롬프트로 복원합니다. 복원도 새 리비전으로 기록되어 이력이 끊기지 않으며, " +
                "현재값과 같은 내용이면 기록 없이 성공합니다(멱등).\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| PROMPT_REVISION_NOT_FOUND | 404 | 존재하지 않는 리비전 |",
    )
    @PostMapping("/prompt/revisions/{revisionId}/restore")
    fun restoreRevision(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AdminPrincipal,
        @PathVariable revisionId: Long,
    ): ApiResponse<PromptSettingResponse> {
        val revision = conflictRetry.execute { promptRevisionService.restore(revisionId, principal.email) }
        return ApiResponse.success(promptResponse(revision.promptType))
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
