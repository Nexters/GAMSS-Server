package com.nexters.gamss.llm.provider

import com.google.genai.Client

/**
 * Gemini 호출 경로 하나. 자기 설정으로 자기 클라이언트를 만들고, 쓸 수 있는지도 스스로 판단한다.
 * 경로가 늘어도 이 인터페이스 구현체만 추가하면 된다.
 */
interface GeminiConnection {
    val provider: LlmProvider

    /** 이 경로의 SDK 클라이언트. 구현체가 한 번만 만들어 재사용한다. */
    fun client(): Client

    /** 이 경로로 전환해도 되는지. 설정이 모자라면 예외를 던진다. */
    fun ensureUsable()
}
