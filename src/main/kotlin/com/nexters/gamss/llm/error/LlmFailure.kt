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
