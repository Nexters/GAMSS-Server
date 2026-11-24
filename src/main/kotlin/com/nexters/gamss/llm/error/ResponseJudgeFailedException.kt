package com.nexters.gamss.llm.error

class ResponseJudgeFailedException(
    message: String,
    cause: Throwable? = null,
    val usedTokens: Int? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    override val kind: LlmFailureKind = LlmFailureKind.VALIDATION,
) : RuntimeException(message, cause),
    LlmFailure
