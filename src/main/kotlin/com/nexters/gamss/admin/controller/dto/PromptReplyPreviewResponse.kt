package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.llm.preview.ReplyPreviewResult
import io.swagger.v3.oas.annotations.media.Schema

/** 답장 미리보기 결과. 생성·검증 실패도 오류 필드로 담아 200으로 내려준다(실패 관찰이 목적). */
data class PromptReplyPreviewResponse(
    @field:Schema(description = "사용한 모델", example = "gemini-3.1-flash-lite")
    val model: String,
    @field:Schema(description = "실제 전달된 시스템 프롬프트(공통+답글 조립본)")
    val systemPrompt: String,
    @field:Schema(description = "실제 전달된 user content")
    val userContent: String,
    @field:Schema(description = "재응답한 캐릭터", example = "ANGER")
    val character: String,
    @field:Schema(description = "캐릭터의 재응답(생성 실패 시 null)", nullable = true)
    val replyText: String?,
    @field:Schema(description = "의미 검증 실패 사유(통과 시 null)", nullable = true)
    val validationError: String?,
    @field:Schema(description = "호출·파싱 실패 사유(성공 시 null)", nullable = true)
    val generationError: String?,
    @field:Schema(description = "총 소비 토큰", example = "1204")
    val usedTokens: Int,
    @field:Schema(description = "캐시된 입력 토큰", example = "800")
    val cachedTokens: Int,
    @field:Schema(description = "입력 토큰", example = "1100")
    val inputTokens: Int,
    @field:Schema(description = "출력 토큰", example = "104")
    val outputTokens: Int,
    @field:Schema(description = "예상 비용(USD)", example = "0.0004")
    val estimatedCostUsd: Double,
    @field:Schema(description = "생성 지연(ms)", example = "1512")
    val latencyMs: Long,
) {
    companion object {
        fun from(result: ReplyPreviewResult): PromptReplyPreviewResponse =
            PromptReplyPreviewResponse(
                model = result.model,
                systemPrompt = result.systemPrompt,
                userContent = result.userContent,
                character = result.character.name,
                replyText = result.replyText,
                validationError = result.validationError,
                generationError = result.generationError,
                usedTokens = result.usage.usedTokens,
                cachedTokens = result.usage.cachedTokens,
                inputTokens = result.usage.inputTokens,
                outputTokens = result.usage.outputTokens,
                estimatedCostUsd = result.usage.estimatedCostUsd,
                latencyMs = result.latencyMs,
            )
    }
}
