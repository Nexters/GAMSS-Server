package com.nexters.gamss.llm.provider

/** Gemini 호출 경로. 행위는 [GeminiConnection] 구현체가 갖고 여기엔 선택지 어휘만 둔다. */
enum class LlmProvider {
    AI_STUDIO,
    VERTEX_AI,
}
