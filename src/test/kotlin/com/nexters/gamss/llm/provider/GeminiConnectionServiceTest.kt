package com.nexters.gamss.llm.provider

import com.google.genai.Client
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeminiConnectionServiceTest {
    private val repository = mockk<LlmProviderSettingRepository>()
    private val aiStudioClient = mockk<Client>()
    private val vertexClient = mockk<Client>()
    private val aiStudio = StubConnection(LlmProvider.AI_STUDIO, aiStudioClient)
    private val vertex = StubConnection(LlmProvider.VERTEX_AI, vertexClient)
    private val service = GeminiConnectionService(listOf(aiStudio, vertex), repository)

    @Test
    fun `설정된 경로의 클라이언트를 준다`() {
        givenRow(LlmProvider.VERTEX_AI)

        assertEquals(vertexClient, service.activeClient())
    }

    @Test
    fun `전환하면 설정이 바뀐다`() {
        val row = givenRow(LlmProvider.AI_STUDIO)

        service.switchTo(LlmProvider.VERTEX_AI)

        assertEquals(LlmProvider.VERTEX_AI, row.provider)
    }

    @Test
    fun `쓸 수 없는 경로로는 전환하지 않는다`() {
        val row = givenRow(LlmProvider.AI_STUDIO)
        vertex.usable = false

        val exception = assertFailsWith<BusinessException> { service.switchTo(LlmProvider.VERTEX_AI) }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        assertEquals(LlmProvider.AI_STUDIO, row.provider)
    }

    @Test
    fun `구현체가 있는 경로만 고를 수 있다`() {
        val onlyAiStudio = GeminiConnectionService(listOf(aiStudio), repository)

        assertEquals(listOf(LlmProvider.AI_STUDIO), onlyAiStudio.availableProviders())
    }

    private fun givenRow(provider: LlmProvider): LlmProviderSetting {
        val row = LlmProviderSetting(provider)
        every { repository.findAll() } returns listOf(row)
        return row
    }

    private class StubConnection(
        override val provider: LlmProvider,
        private val client: Client,
        var usable: Boolean = true,
    ) : GeminiConnection {
        override fun client(): Client = client

        override fun ensureUsable() {
            if (!usable) {
                throw BusinessException(ErrorCode.INVALID_INPUT, "설정 없음")
            }
        }
    }
}
