package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

/** 앱 전체 단일 모델 설정. 댓글·답글·카드 생성과 카드 감정 분류가 모두 이 모델을 쓴다. */
data class ModelSettingResponse(
    @field:Schema(description = "현재 적용 중인 모델", example = "gemini-3.1-flash-lite")
    val model: String,
    @field:Schema(description = "선택 가능한 모델 목록(Gemini API에서 동적 조회)")
    val availableModels: List<String>,
    @field:Schema(description = "코드 기본값 모델(기본값으로 복원용)", example = "gemini-3.1-flash-lite")
    val defaultModel: String,
)
