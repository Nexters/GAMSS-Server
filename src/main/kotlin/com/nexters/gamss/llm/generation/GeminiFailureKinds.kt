package com.nexters.gamss.llm.generation

import com.google.genai.errors.ApiException
import com.google.genai.errors.GenAiIOException
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.llm.error.LlmFailureKind

/**
 * google-genai SDK 예외를 우리 실패 종류로 번역한다. **SDK 예외 타입을 아는 유일한 자리**이며,
 * 제너레이터 셋이 이 함수를 공유한다 - 재시도 정책, 서비스로는 SDK가 새어나가지 않는다.
 *
 * `GenAiIOException`과 `ApiException`을 따로 짚는 이유는 둘의 공통 부모(`BaseException`)가
 * SDK 패키지 밖에서 보이지 않기 때문이다. 호출 타임아웃은 `SocketTimeoutException`을 감싼
 * `GenAiIOException`으로 올라온다.
 *
 * SDK 예외만 오는 자리가 아니다. 제너레이터는 호출 연결을 얻는 것까지 같은 try 안에서 하므로
 * ([com.nexters.gamss.llm.provider.GeminiConnectionService.active] 와
 * [com.nexters.gamss.llm.provider.GeminiConnection.client]), 인증 설정이 비었을 때 나는
 * `BusinessException` 도 여기로 들어온다.
 */
internal object GeminiFailureKinds {
    fun of(error: Throwable): LlmFailureKind =
        when {
            error is GenAiIOException -> LlmFailureKind.CALL

            error is ApiException -> ofStatus(error.code())

            // 호출 경로의 인증 설정이 비었거나 깨졌다(AiStudioConnection, VertexAiConnection).
            // 사람이 설정을 고치기 전에는 몇 번을 불러도 같은 답이라 4xx 와 같은 자리에 둔다.
            // 재시도로 두면 요청 하나가 세 번을 다 돌고 끝나고, 그 실패가 서킷 집계에도 섞인다.
            error is BusinessException -> LlmFailureKind.PERMANENT

            // SDK 밖의 예기치 않은 오류. 재시도 대상으로 두는 편이 지금까지의 동작과 같다.
            else -> LlmFailureKind.CALL
        }

    private fun ofStatus(status: Int): LlmFailureKind =
        when {
            status == 429 -> LlmFailureKind.RATE_LIMITED

            // 408은 4xx지만 "요청이 늦었다"라 다시 부를 값어치가 있다.
            status == 408 -> LlmFailureKind.CALL

            // 나머지 4xx는 인증·권한·요청 형식 문제라 몇 번을 불러도 같은 답이 온다.
            status in 400..499 -> LlmFailureKind.PERMANENT

            else -> LlmFailureKind.CALL
        }
}
