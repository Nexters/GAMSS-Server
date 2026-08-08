package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.llm.settings.PromptRevision
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class PromptRevisionDetailResponse(
    @field:Schema(description = "리비전 ID", example = "12")
    val id: Long,
    @field:Schema(description = "프롬프트 타입", example = "COMMENT")
    val promptType: String,
    @field:Schema(description = "타입 안에서 1부터 증가하는 버전", example = "7")
    val version: Int,
    @field:Schema(description = "저장한 관리자 이메일 (시스템 기록이면 null)", example = "admin@gamss.kr", nullable = true)
    val savedBy: String?,
    @field:Schema(description = "이 저장이 복원이라면 출처 버전", example = "3", nullable = true)
    val restoredFromVersion: Int?,
    @field:Schema(description = "저장 시각")
    val createdAt: Instant,
    @field:Schema(description = "이 버전의 시스템 프롬프트 전문")
    val systemPrompt: String,
) {
    companion object {
        fun from(revision: PromptRevision): PromptRevisionDetailResponse =
            PromptRevisionDetailResponse(
                id = revision.id,
                promptType = revision.promptType.name,
                version = revision.version,
                savedBy = revision.savedBy,
                restoredFromVersion = revision.restoredFromVersion,
                createdAt = revision.createdAt,
                systemPrompt = revision.systemPrompt,
            )
    }
}
