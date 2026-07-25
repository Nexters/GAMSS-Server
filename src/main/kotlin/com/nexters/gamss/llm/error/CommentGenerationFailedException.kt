package com.nexters.gamss.llm.error

/** LLM 호출 실패(네트워크·타임아웃) 또는 응답의 의미 검증 실패를 모두 아우르는 예외. 재시도 대상. */
class CommentGenerationFailedException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
