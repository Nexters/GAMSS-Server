package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.support.RepositoryTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * 토큰 상한 정책 단일 행(V15 시드)의 조회·수정을 실제 MySQL 로 검증한다.
 */
class TokenPolicyServiceIntegrationTest : RepositoryTest() {
    @Autowired private lateinit var tokenPolicyService: TokenPolicyService

    @Test
    fun `시드 기본값을 조회한다`() {
        val policy = tokenPolicyService.current()

        assertEquals(100_000, policy.dailyTokenLimit)
        assertEquals(5, policy.resetHour)
    }

    @Test
    fun `상한과 리셋 시각을 수정하면 단일 행에 반영된다`() {
        tokenPolicyService.update(dailyTokenLimit = 250_000, resetHour = 9)

        val policy = tokenPolicyService.current()
        assertEquals(250_000, policy.dailyTokenLimit)
        assertEquals(9, policy.resetHour)
    }
}
