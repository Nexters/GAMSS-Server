package com.nexters.gamss.llm.generation

/** 카드 대사 LLM 호출 실패(네트워크·타임아웃) 또는 응답 파싱 실패를 아우르는 예외. 재시도 대상. */
class CardGenerationFailedException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
