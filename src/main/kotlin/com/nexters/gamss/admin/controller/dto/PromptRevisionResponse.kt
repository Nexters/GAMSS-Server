package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.llm.settings.PromptRevision
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/** 리비전 목록 항목. 본문 전체는 무거워서 미리보기만 담고, 전체는 상세 조회로 받는다. */
data class PromptRevisionResponse(
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
    @field:Schema(description = "프롬프트 전체 길이(자)", example = "1840")
    val length: Int,
    @field:Schema(description = "본문 미리보기(개행 제거, 앞 100자)")
    val preview: String,
) {
    companion object {
        private const val PREVIEW_LENGTH = 100

        fun from(revision: PromptRevision): PromptRevisionResponse =
            PromptRevisionResponse(
                id = revision.id,
                promptType = revision.promptType.name,
                version = revision.version,
                savedBy = revision.savedBy,
                restoredFromVersion = revision.restoredFromVersion,
                createdAt = revision.createdAt,
                length = revision.systemPrompt.length,
                preview = revision.systemPrompt.replace('\n', ' ').take(PREVIEW_LENGTH),
            )
    }
}
