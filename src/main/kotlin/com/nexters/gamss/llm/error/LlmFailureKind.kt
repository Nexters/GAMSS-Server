package com.nexters.gamss.llm.error

/**
 * LLM 생성이 실패한 원인의 종류. 재시도 여부와 시도 사이 대기 간격이 이 값 하나로 갈린다.
 *
 * SDK 타입(`ApiException` 등)은 전송 호출 자리 밖으로 나가지 않으므로,
 * [com.nexters.gamss.llm.generation.GeminiCaller]가 SDK 예외를 이 값으로 번역해 예외에 실어 보낸다 —
 * 덕분에 재시도 정책([com.nexters.gamss.llm.generation.LlmFailureRetryPolicy])은 Gemini를 모른 채로 남는다.
 */
enum class LlmFailureKind {
    /** 네트워크·IO·5xx. 상대가 아픈 것이라 짧게 쉬었다 다시 부른다. */
    CALL,

    /**
     * 429 쿼터 초과. "상대가 아프다"가 아니라 "우리가 너무 빨리 부른다"라서 짧게 기다리면 그대로 또
     * 429다. SDK가 응답 헤더를 노출하지 않아 `Retry-After`를 읽을 수 없어 고정 간격을 길게 잡는다.
     */
    RATE_LIMITED,

    /**
     * 400·401·403. 인증·권한·요청 형식 문제라 100번 불러도 같은 결과다. 재시도하면 확실히 실패할 것을
     * 알면서 사용자를 더 기다리게 하는 셈이라 즉시 중단한다.
     */
    PERMANENT,

    /**
     * 응답 파싱·의미 검증 실패. Gemini는 멀쩡하고 형식만 틀린 것이라 기다릴 이유가 없다 — 재시도는
     * 하되 간격 없이 곧바로 다시 부른다.
     */
    VALIDATION,

    /**
     * 서킷브레이커가 열려 호출이 나가지 못했다. "업스트림이 아프다"는 판정이 이미 끝난 상태라 다시
     * 불러야 SDK까지 가지도 못하고 같은 자리에서 막힌다. 재시도도 대기도 사용자를 기다리게 할 뿐이라
     * 즉시 중단한다([com.nexters.gamss.llm.generation.GeminiCircuitPolicy]).
     *
     * 이것이 예외 타입이 아니라 종류인 이유는 [LlmFailure] KDoc에 있다 — 재시도 금지 실패를
     * 서브클래스로 만들면 `retryOn` 타입 판정에 걸려 되레 재시도된다.
     */
    CIRCUIT_OPEN,
}
