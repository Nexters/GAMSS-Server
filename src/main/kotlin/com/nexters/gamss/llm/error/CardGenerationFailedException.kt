package com.nexters.gamss.llm.error

/**
 * 카드 생성 경로(감정 분류·카드 한 줄)의 LLM 호출 실패(네트워크·타임아웃) 또는 응답 파싱 실패를
 * 아우르는 예외.
 *
 * 재시도할지는 타입이 아니라 [kind]가 정한다 — 기본값의 의미는 [CommentGenerationFailedException]과 같다.
 *
 * 토큰 필드는 "호출은 됐지만 이후 파싱에서 실패한" 경우에 채워진다 — 실제 과금된 토큰을 실패 로그에도
 * 남겨 비용·캐시 집계에서 빠지지 않게 한다(호출 자체가 실패했으면 null).
 */
class CardGenerationFailedException(
    message: String,
    cause: Throwable? = null,
    val usedTokens: Int? = null,
    val cachedTokens: Int? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    override val kind: LlmFailureKind = LlmFailureKind.VALIDATION,
) : RuntimeException(message, cause),
    LlmFailure
