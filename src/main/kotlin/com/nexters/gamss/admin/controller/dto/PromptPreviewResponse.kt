package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.llm.preview.PromptPreviewResult
import io.swagger.v3.oas.annotations.media.Schema

/** 미리보기 결과. 생성·검증 실패도 오류 필드로 담아 200으로 내려준다(실패 관찰이 목적). */
data class PromptPreviewResponse(
    @field:Schema(description = "사용한 모델", example = "gemini-3.1-flash-lite")
    val model: String,
    @field:Schema(description = "실제 전달된 시스템 프롬프트(공통+댓글 조립본)")
    val systemPrompt: String,
    @field:Schema(description = "실제 전달된 user content")
    val userContent: String,
    @field:Schema(description = "등장 캐릭터(무작위 선택 결과 포함)")
    val characters: List<String>,
    @field:Schema(description = "티키타카 개수", example = "1")
    val tikitakaCount: Int,
    @field:Schema(description = "엉뚱이 소재(엉뚱이 등장 시)", nullable = true)
    val eongttungTopic: String?,
    @field:Schema(description = "생성된 댓글 목록(생성 실패 시 null)", nullable = true)
    val comments: List<PreviewCommentItem>?,
    @field:Schema(description = "생성된 티키타카 목록(생성 실패 시 null)", nullable = true)
    val tikitaka: List<PreviewTikitakaItem>?,
    @field:Schema(description = "의미 검증 실패 사유(통과 시 null)", nullable = true)
    val validationError: String?,
    @field:Schema(description = "호출·파싱 실패 사유(성공 시 null)", nullable = true)
    val generationError: String?,
    @field:Schema(description = "총 소비 토큰", example = "1834")
    val usedTokens: Int,
    @field:Schema(description = "캐시된 입력 토큰", example = "1200")
    val cachedTokens: Int,
    @field:Schema(description = "입력 토큰", example = "1600")
    val inputTokens: Int,
    @field:Schema(description = "출력 토큰", example = "234")
    val outputTokens: Int,
    @field:Schema(description = "예상 비용(USD)", example = "0.0007")
    val estimatedCostUsd: Double,
    @field:Schema(description = "생성 지연(ms)", example = "2814")
    val latencyMs: Long,
) {
    data class PreviewCommentItem(
        @field:Schema(description = "캐릭터", example = "ANGER")
        val characterId: String,
        @field:Schema(description = "댓글 내용")
        val text: String,
    )

    data class PreviewTikitakaItem(
        @field:Schema(description = "말하는 캐릭터", example = "GRUMPY")
        val characterId: String,
        @field:Schema(description = "답장 대상 캐릭터", example = "ANGER")
        val replyTo: String,
        @field:Schema(description = "대댓글 내용")
        val text: String,
    )

    companion object {
        fun from(result: PromptPreviewResult): PromptPreviewResponse =
            PromptPreviewResponse(
                model = result.model,
                systemPrompt = result.systemPrompt,
                userContent = result.userContent,
                characters = result.characters.map { it.name },
                tikitakaCount = result.tikitakaCount,
                eongttungTopic = result.eongttungTopic,
                comments = result.feed?.comments?.map { PreviewCommentItem(it.characterId.name, it.text) },
                tikitaka = result.feed?.tikitaka?.map { PreviewTikitakaItem(it.characterId.name, it.replyTo.name, it.text) },
                validationError = result.validationError,
                generationError = result.generationError,
                usedTokens = result.usedTokens,
                cachedTokens = result.cachedTokens,
                inputTokens = result.inputTokens,
                outputTokens = result.outputTokens,
                estimatedCostUsd = result.estimatedCostUsd,
                latencyMs = result.latencyMs,
            )
    }
}
