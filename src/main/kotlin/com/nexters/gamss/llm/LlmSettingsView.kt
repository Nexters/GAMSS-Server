package com.nexters.gamss.llm

/** 현재 적용 중인 LLM 설정(DB 값 또는 코드 기본값). */
data class LlmSettingsView(
    val model: String,
    val systemPrompt: String,
)
