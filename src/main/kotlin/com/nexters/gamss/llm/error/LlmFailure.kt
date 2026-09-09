package com.nexters.gamss.llm.error

/**
 * 실패 종류를 실어 나르는 LLM 생성 예외의 공통 계약이다. 댓글 계열([CommentGenerationFailedException])과
 * 카드 계열([CardGenerationFailedException])이 각각 구현해, 재시도 정책이 두 계열을 한 가지 방식으로
 * 읽는다.
 *
 * 실패 종류를 서브클래스가 아니라 필드로 둔 이유가 있다 — 재시도 대상 판정이 예외 **타입**으로
 * 이뤄지기 때문이다([com.nexters.gamss.llm.generation.LlmRetryExecutor]의 `retryOn`). 재시도 금지
 * 실패를 서브클래스로 만들면 그 타입 판정에 그대로 걸려 재시도돼 버린다.
 */
interface LlmFailure {
    val kind: LlmFailureKind
}

/**
 * 이 예외가 실은 어떤 LLM 실패였는지. LLM 실패가 아니면 null.
 *
 * 생성 경로는 LLM 실패를 `BusinessException` 으로 갈아 끼워 올리므로(`initCause`), 부르는 쪽이 손에
 * 쥐는 것은 껍데기다. 원인 사슬을 훑어야 종류가 보인다.
 */
fun Throwable.llmFailureKind(): LlmFailureKind? =
    generateSequence(this) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .firstNotNullOfOrNull { (it as? LlmFailure)?.kind }

/** 사슬을 도는 예외가 들어와도 멈추게 한다. 실제로 싸이는 겹은 하나뿐이다. */
private const val MAX_CAUSE_DEPTH = 5
