package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.card.domain.CardSummary
import com.nexters.gamss.llm.preview.CardPreviewResult
import io.swagger.v3.oas.annotations.media.Schema

/** 카드 한 줄 미리보기 결과. 생성 실패도 오류 필드로 담아 200으로 내려준다(실패 관찰이 목적). */
data class PromptCardPreviewResponse(
    @field:Schema(description = "사용한 모델", example = "gemini-3.1-flash-lite")
    val model: String,
    @field:Schema(description = "실제 전달된 시스템 프롬프트(카드 프롬프트 단독 — 공통은 붙지 않음)")
    val systemPrompt: String,
    @field:Schema(description = "실제 전달된 user content")
    val userContent: String,
    @field:Schema(description = "실제 카드에 붙을 대표 감정. 판정이 NONSENSE면 요청한 감정과 관계없이 QUIRKY", example = "ANGER")
    val emotion: String,
    @field:Schema(
        description = "카드 한 줄 판정. EVENT는 유저 시점의 하루 기록, NONSENSE는 알아볼 수 있는 내용이 없어 엉뚱이 소재를 한 줄로 남긴 경우. 판정 호출이 실패하면 null",
        example = "EVENT",
        allowableValues = ["EVENT", "NONSENSE"],
        nullable = true,
    )
    val kind: String?,
    @field:Schema(description = "한 줄로 쓴 엉뚱이 소재(NONSENSE일 때만). 소재 목록의 문장 그대로다", example = "목마르다", nullable = true)
    val eongttungTopic: String?,
    @field:Schema(description = "실제 저장될 한 줄(생성 실패 시 null)", example = "오늘 팀장이 자기 할 일을 다 떠넘겼어요", nullable = true)
    val line: String?,
    @field:Schema(description = "저장되는 한 줄의 길이(공백 포함)", example = "21", nullable = true)
    val length: Int?,
    @field:Schema(description = "다듬기 전 한 줄(EVENT면 LLM 원문, NONSENSE면 소재 문장)", nullable = true)
    val rawLine: String?,
    @field:Schema(description = "자르기 전 길이(공백 포함)", example = "58", nullable = true)
    val rawLength: Int?,
    @field:Schema(description = "상한(${CardSummary.MAX_LENGTH}자)을 넘겨 서버가 잘랐는지", example = "false")
    val truncated: Boolean,
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
        fun from(result: CardPreviewResult): PromptCardPreviewResponse =
            PromptCardPreviewResponse(
                model = result.model,
                systemPrompt = result.systemPrompt,
                userContent = result.userContent,
                emotion = result.emotion.name,
                kind = result.kind?.name,
                eongttungTopic = result.eongttungTopic,
                line = result.line,
                // 관리자가 세는 글자 수와 서버가 자르는 기준이 같아야 한다(UTF-16 유닛이 아니라 그래핌).
                length = result.line?.let { CardSummary.graphemeCount(it) },
                rawLine = result.rawLine,
                rawLength = result.rawLength,
                truncated = result.truncated,
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
