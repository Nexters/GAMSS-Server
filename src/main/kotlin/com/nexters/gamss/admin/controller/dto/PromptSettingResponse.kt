package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

/** 타입별 프롬프트 설정(원본, 공통과 조립 전). 과거 버전으로의 복원은 리비전 API가 담당한다. */
data class PromptSettingResponse(
    @field:Schema(description = "프롬프트 타입", example = "COMMENT", allowableValues = ["COMMON", "COMMENT", "REPLY", "CARD"])
    val promptType: String,
    @field:Schema(description = "현재 적용 중인 시스템 프롬프트(이 타입의 원본)")
    val systemPrompt: String,
)
