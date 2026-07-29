package com.nexters.gamss.llm.error

/**
 * LLM 호출 실패(네트워크·타임아웃) 또는 응답의 의미 검증 실패를 모두 아우르는 예외. 재시도 대상.
 *
 * 토큰 필드는 "호출은 됐지만 이후 파싱·검증에서 실패한" 경우에 채워진다 — 실제 과금된 토큰을 실패 로그에도
 * 남겨 비용·캐시 집계에서 빠지지 않게 한다(호출 자체가 실패했으면 null).
 */
class CommentGenerationFailedException(
    message: String,
    cause: Throwable? = null,
    val usedTokens: Int? = null,
    val cachedTokens: Int? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
) : RuntimeException(message, cause)
