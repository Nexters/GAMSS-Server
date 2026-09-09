package com.nexters.gamss.llm.generation

import com.google.genai.errors.ClientException
import com.google.genai.errors.GenAiIOException
import com.google.genai.errors.ServerException
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.error.LlmFailureKind
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class GeminiFailureKindsTest {
    @Test
    fun `429는 쿼터 초과로 분류한다`() {
        val error = ClientException(429, "RESOURCE_EXHAUSTED", "quota exceeded")

        assertEquals(LlmFailureKind.RATE_LIMITED, GeminiFailureKinds.of(error))
    }

    @Test
    fun `400·401·403은 다시 불러도 같으므로 영구 실패로 분류한다`() {
        listOf(400, 401, 403).forEach { status ->
            val error = ClientException(status, "INVALID_ARGUMENT", "bad request")

            assertEquals(LlmFailureKind.PERMANENT, GeminiFailureKinds.of(error), "status=$status")
        }
    }

    @Test
    fun `408은 4xx지만 다시 부를 값어치가 있어 호출 실패로 분류한다`() {
        val error = ClientException(408, "REQUEST_TIMEOUT", "timeout")

        assertEquals(LlmFailureKind.CALL, GeminiFailureKinds.of(error))
    }

    @Test
    fun `5xx는 상대가 아픈 것이라 호출 실패로 분류한다`() {
        val error = ServerException(503, "UNAVAILABLE", "overloaded")

        assertEquals(LlmFailureKind.CALL, GeminiFailureKinds.of(error))
    }

    @Test
    fun `네트워크·타임아웃은 호출 실패로 분류한다`() {
        // per-attempt 타임아웃은 SocketTimeoutException을 감싼 GenAiIOException으로 올라온다.
        val error = GenAiIOException("read timed out", IOException("socket"))

        assertEquals(LlmFailureKind.CALL, GeminiFailureKinds.of(error))
    }

    /**
     * 인증 설정이 비면 제너레이터가 연결을 얻는 단계에서 BusinessException 을 만난다. 이것을 CALL 로
     * 두면 사람이 설정을 고치기 전에는 절대 성공하지 않을 호출을 세 번씩 반복한다.
     */
    @Test
    fun `인증 설정이 빠져 생긴 실패는 영구 실패로 분류한다`() {
        val error = BusinessException(ErrorCode.INVALID_INPUT, "AI Studio API 키가 설정되지 않았습니다.")

        assertEquals(LlmFailureKind.PERMANENT, GeminiFailureKinds.of(error))
    }

    @Test
    fun `SDK 밖의 예기치 않은 오류는 재시도 대상으로 남긴다`() {
        assertEquals(LlmFailureKind.CALL, GeminiFailureKinds.of(IllegalStateException("어디선가 터진 오류")))
    }
}
