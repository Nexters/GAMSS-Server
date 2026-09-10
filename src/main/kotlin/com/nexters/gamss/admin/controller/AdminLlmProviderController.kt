package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.LlmProviderResponse
import com.nexters.gamss.admin.controller.dto.UpdateLlmProviderRequest
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.llm.provider.GeminiConnectionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "백오피스 LLM 호출 경로",
    description =
        "Gemini 를 AI Studio(API 키)로 호출할지 Vertex AI(서비스 계정)로 호출할지 조회·전환 (ROLE_ADMIN 필요). " +
            "재배포 없이 다음 생성부터 반영되며, 모델·프롬프트 설정은 경로와 무관하게 그대로 쓰인다.",
)
@RestController
@RequestMapping("/api/admin/llm-settings/provider")
class AdminLlmProviderController(
    private val connections: GeminiConnectionService,
) {
    @Operation(summary = "호출 경로 조회")
    @GetMapping
    fun getProvider(): ApiResponse<LlmProviderResponse> = ApiResponse.success(response())

    @Operation(
        summary = "호출 경로 전환",
        description =
            "대상 경로의 인증 설정이 갖춰져 있을 때만 전환합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | provider 누락, 또는 대상 경로의 인증 설정 누락 |",
    )
    @PutMapping
    fun updateProvider(
        @Valid @RequestBody request: UpdateLlmProviderRequest,
    ): ApiResponse<LlmProviderResponse> {
        connections.switchTo(checkNotNull(request.provider))
        return ApiResponse.success(response())
    }

    private fun response(): LlmProviderResponse =
        LlmProviderResponse(
            provider = connections.active().provider,
            availableProviders = connections.availableProviders(),
        )
}
