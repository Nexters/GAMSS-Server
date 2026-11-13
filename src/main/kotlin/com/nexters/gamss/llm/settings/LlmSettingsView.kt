package com.nexters.gamss.llm.settings

/** 특정 [com.nexters.gamss.llm.prompt.PromptType]에 대해 현재 적용 중인 LLM 설정. */
data class LlmSettingsView(
    val model: String,
    val systemPrompt: String,
    val promptVersions: PromptVersions = PromptVersions.UNKNOWN,
)
