package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.monitoring.repository.GenerationLogRepository
import com.nexters.gamss.tokenlimit.domain.TokenPolicy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyTokenLimitServiceTest {
    private val tokenPolicyService = mockk<TokenPolicyService>()
    private val generationLogRepository = mockk<GenerationLogRepository>()

    private fun service(enabled: Boolean) = DailyTokenLimitService(tokenPolicyService, generationLogRepository, enabled)

    @Test
    fun `상한이 꺼진 환경(dev)에서는 사용량과 무관하게 항상 통과시킨다`() {
        val service = service(enabled = false)

        assertTrue(service.isWithinLimit(1L))
        // 미적용 환경에서는 정책·사용량 조회조차 하지 않는다.
        verify(exactly = 0) { tokenPolicyService.current() }
        verify(exactly = 0) { generationLogRepository.sumUsedTokensByMemberSince(any(), any()) }
    }

    @Test
    fun `사용량이 상한 미만이면 통과시킨다`() {
        every { tokenPolicyService.current() } returns TokenPolicy(dailyTokenLimit = 100_000, resetHour = 5)
        every { generationLogRepository.sumUsedTokensByMemberSince(eq(1L), any<Instant>()) } returns 99_999

        assertTrue(service(enabled = true).isWithinLimit(1L))
    }

    @Test
    fun `사용량이 상한에 닿으면 차단한다`() {
        every { tokenPolicyService.current() } returns TokenPolicy(dailyTokenLimit = 100_000, resetHour = 5)
        every { generationLogRepository.sumUsedTokensByMemberSince(eq(1L), any<Instant>()) } returns 100_000

        assertFalse(service(enabled = true).isWithinLimit(1L))
    }
}
