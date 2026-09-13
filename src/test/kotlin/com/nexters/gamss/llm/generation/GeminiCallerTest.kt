package com.nexters.gamss.llm.generation

import com.google.genai.Client
import com.google.genai.Models
import com.google.genai.errors.ServerException
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import com.nexters.gamss.llm.error.LlmFailureKind
import com.nexters.gamss.llm.provider.GeminiConnection
import com.nexters.gamss.llm.provider.GeminiConnectionService
import com.nexters.gamss.llm.provider.LlmProvider
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * 전송 호출 한 자리에 서킷이 제대로 물렸는지 본다. 상태 전이 자체는 [GeminiCircuitBreakersTest] 가
 * 보고, 여기서는 **번역과 배선**을 본다 - 서킷이 막은 호출이 재시도 금지 종류로 올라오는지, 전송
 * 실패가 실제로 서킷에 집계되는지다.
 */
class GeminiCallerTest {
    private val models = mockk<Models>()
    private val connections = mockk<GeminiConnectionService>()
    private val breakers = GeminiCircuitBreakers()
    private val caller = GeminiCaller(connections, breakers)

    init {
        every { connections.active() } returns StubConnection(LlmProvider.AI_STUDIO, clientWith(models))
    }

    @Test
    fun `성공하면 응답을 그대로 돌려준다`() {
        val response = mockk<GenerateContentResponse>()
        every { models.generateContent(any<String>(), any<String>(), any()) } returns response

        assertSame(response, call())
    }

    @Test
    fun `전송 실패는 실패 종류로 번역해 부르는 쪽 예외에 싣는다`() {
        every { models.generateContent(any<String>(), any<String>(), any()) } throws ServerException(503, "UNAVAILABLE", "overloaded")

        val thrown = assertFailsWith<CommentGenerationFailedException> { call() }

        assertEquals(LlmFailureKind.CALL, thrown.kind)
    }

    @Test
    fun `전송 실패가 임계에 닿으면 다음 호출은 SDK 까지 가지 않는다`() {
        every { models.generateContent(any<String>(), any<String>(), any()) } throws ServerException(503, "UNAVAILABLE", "overloaded")
        repeat(GeminiCircuitPolicy.MINIMUM_NUMBER_OF_CALLS) { runCatching { call() } }

        val thrown = assertFailsWith<CommentGenerationFailedException> { call() }

        assertEquals(LlmFailureKind.CIRCUIT_OPEN, thrown.kind)
        // 임계를 채운 호출까지만 나가고 그 뒤로는 늘지 않는다. 이것이 스레드와 시간을 아끼는 자리다.
        verify(exactly = GeminiCircuitPolicy.MINIMUM_NUMBER_OF_CALLS) {
            models.generateContent(any<String>(), any<String>(), any())
        }
    }

    @Test
    fun `서킷이 막은 호출은 원인에 CallNotPermittedException 을 남긴다`() {
        // GenerationLog.failure_reason 이 cause 의 클래스명을 쓰므로, 이 원인이 그대로 기록에 남아야
        // 네트워크 실패와 서킷 오픈이 구분된다.
        every { models.generateContent(any<String>(), any<String>(), any()) } throws ServerException(503, "UNAVAILABLE", "overloaded")
        repeat(GeminiCircuitPolicy.MINIMUM_NUMBER_OF_CALLS) { runCatching { call() } }

        val thrown = assertFailsWith<CommentGenerationFailedException> { call() }

        assertIs<CallNotPermittedException>(thrown.cause)
    }

    @Test
    fun `연결을 얻지 못한 실패도 같은 자리에서 번역한다`() {
        // 인증 설정이 비면 전송까지 가지도 못한다. 사람이 고치기 전에는 같은 답이라 재시도 대상이 아니다.
        every { connections.active() } throws BusinessException(ErrorCode.INVALID_INPUT, "AI Studio API 키가 설정되지 않았습니다.")

        val thrown = assertFailsWith<CommentGenerationFailedException> { call() }

        assertEquals(LlmFailureKind.PERMANENT, thrown.kind)
    }

    private fun call(): GenerateContentResponse =
        caller.call("gemini-3.1-flash-lite", "유저 콘텐츠", GenerateContentConfig.builder().build()) { cause, kind ->
            CommentGenerationFailedException("LLM 호출에 실패했습니다.", cause, kind = kind)
        }

    /** `Client.models` 는 생성자로만 채워지는 final 필드라, 목 객체에는 리플렉션으로 끼워 넣는다. */
    private fun clientWith(models: Models): Client =
        mockk<Client>().also { client ->
            Client::class.java
                .getDeclaredField("models")
                .apply { isAccessible = true }
                .set(client, models)
        }

    private class StubConnection(
        override val provider: LlmProvider,
        private val client: Client,
    ) : GeminiConnection {
        override fun client(): Client = client

        override fun ensureUsable() = Unit
    }
}
