package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.monitoring.service.GenerationLogReadService
import com.nexters.gamss.tokenlimit.domain.TokenPolicy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyTokenLimitServiceTest {
    private val tokenPolicyService = mockk<TokenPolicyService>()
    private val generationLogReadService = mockk<GenerationLogReadService>()

    private fun service(enabled: Boolean) = DailyTokenLimitService(tokenPolicyService, generationLogReadService, enabled)

    @Test
    fun `상한이 꺼진 환경(dev)에서는 사용량과 무관하게 항상 통과시킨다`() {
        val service = service(enabled = false)

        assertTrue(service.isWithinLimit(1L))
        // 미적용 환경에서는 정책·사용량 조회조차 하지 않는다.
        verify(exactly = 0) { tokenPolicyService.current() }
        verify(exactly = 0) { generationLogReadService.sumUsedTokensByMemberSince(any(), any()) }
    }

    @Test
    fun `사용량이 상한 미만이면 통과시킨다`() {
        every { tokenPolicyService.current() } returns TokenPolicy(dailyTokenLimit = 100_000, resetHour = 5)
        every { generationLogReadService.sumUsedTokensByMemberSince(eq(1L), any<Instant>()) } returns 99_999

        assertTrue(service(enabled = true).isWithinLimit(1L))
    }

    @Test
    fun `사용량이 상한에 닿으면 차단한다`() {
        every { tokenPolicyService.current() } returns TokenPolicy(dailyTokenLimit = 100_000, resetHour = 5)
        every { generationLogReadService.sumUsedTokensByMemberSince(eq(1L), any<Instant>()) } returns 100_000

        assertFalse(service(enabled = true).isWithinLimit(1L))
    }

    @Test
    fun `상한이 꺼진 환경에서는 사용량은 그대로 보여주되 상한은 null(무제한)이다`() {
        every { tokenPolicyService.current() } returns TokenPolicy(dailyTokenLimit = 100_000, resetHour = 5)
        every { generationLogReadService.sumUsedTokensByMemberSince(eq(1L), any<Instant>()) } returns 12_000

        val usage = service(enabled = false).usageFor(1L)

        assertEquals(12_000, usage.usedTokens)
        assertNull(usage.dailyLimit)
        assertFalse(usage.exceeded)
    }

    @Test
    fun `상한이 켜진 환경에서는 사용량이 상한에 닿으면 초과로 표시한다`() {
        every { tokenPolicyService.current() } returns TokenPolicy(dailyTokenLimit = 100_000, resetHour = 5)
        every { generationLogReadService.sumUsedTokensByMemberSince(eq(1L), any<Instant>()) } returns 100_000

        val usage = service(enabled = true).usageFor(1L)

        assertEquals(100_000, usage.usedTokens)
        assertEquals(100_000, usage.dailyLimit)
        assertTrue(usage.exceeded)
    }

    @Test
    fun `상한이 켜진 환경에서 사용량이 상한 미만이면 초과가 아니다`() {
        every { tokenPolicyService.current() } returns TokenPolicy(dailyTokenLimit = 100_000, resetHour = 5)
        every { generationLogReadService.sumUsedTokensByMemberSince(eq(1L), any<Instant>()) } returns 99_999

        val usage = service(enabled = true).usageFor(1L)

        assertEquals(99_999, usage.usedTokens)
        assertEquals(100_000, usage.dailyLimit)
        assertFalse(usage.exceeded)
    }
}
