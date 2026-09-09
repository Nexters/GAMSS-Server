package com.nexters.gamss.llm.provider

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test

/**
 * 이 점검의 계약은 "알려주되 막지 않는다" 하나다. 여기서 예외가 새 나가면 기동이 실패하는데,
 * 그러면 지금 안 쓰는 경로의 값이 없다는 이유로 배포가 죽어 되돌릴 길까지 막힌다.
 */
class GeminiConnectionStartupCheckTest {
    private val connections = mockk<GeminiConnectionService>()
    private val check = GeminiConnectionStartupCheck(connections)

    @Test
    fun `쓸 수 있는 경로면 그대로 지나간다`() {
        every { connections.active() } returns usableConnection()

        check.verifyActiveConnection()
    }

    @Test
    fun `인증 설정이 없어도 기동을 막지 않는다`() {
        every { connections.active() } returns unusableConnection()

        check.verifyActiveConnection()
    }

    /** 경로 조회는 DB 를 읽는다. 그쪽이 흔들려도 기동은 계속돼야 한다. */
    @Test
    fun `경로 조회가 실패해도 기동을 막지 않는다`() {
        every { connections.active() } throws IllegalStateException("llm_provider_setting 행이 없습니다.")

        check.verifyActiveConnection()
    }

    private fun usableConnection(): GeminiConnection =
        mockk(relaxed = true) {
            every { provider } returns LlmProvider.AI_STUDIO
            every { ensureUsable() } returns Unit
        }

    private fun unusableConnection(): GeminiConnection =
        mockk(relaxed = true) {
            every { provider } returns LlmProvider.VERTEX_AI
            every { ensureUsable() } throws
                BusinessException(ErrorCode.INVALID_INPUT, "Vertex AI 설정이 없습니다: gemini.vertex.credentials-base64")
        }
}
