package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.tokenlimit.domain.TokenPolicy
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 적립이 조용히 실패하면 그 회원의 한도가 사실상 꺼진다. 그래서 "삼키되 반드시 드러낸다"가 이
 * 클래스의 계약이고, 여기서 그 계약만 고정한다.
 */
class TokenQuotaRecorderTest {
    private val tokenPolicyService = mockk<TokenPolicyService>()
    private val repository = mockk<TokenQuotaUsageRepository>()
    private val meterRegistry = SimpleMeterRegistry()
    private val recorder = TokenQuotaRecorder(tokenPolicyService, repository, meterRegistry)

    @Test
    fun `사용한 토큰을 주체에 적립한다`() {
        givenResetHour(5)
        every { repository.addUsedTokens(any(), any(), any()) } returns 1

        recorder.record(MEMBER_ID, 1_200)

        verify { repository.addUsedTokens(MEMBER_ID, any(), 1_200L) }
    }

    /** 쓸 것이 없으면 쓰지 않는다. 실패한 시도가 토큰을 못 쓴 경우가 이에 해당한다. */
    @Test
    fun `토큰을 쓰지 않았으면 적립하지 않는다`() {
        recorder.record(MEMBER_ID, 0)

        verify(exactly = 0) { repository.addUsedTokens(any(), any(), any()) }
    }

    /**
     * 매핑이 없으면 upsert 의 insert...select 가 고를 행이 없어 0 행이 된다. 예외가 아니라 조용한
     * 무효과라 카운터와 로그로 드러내야 한다 - 그 회원만 한도가 꺼진 상태다.
     */
    @Test
    fun `주체 매핑이 없으면 실패로 집계한다`() {
        givenResetHour(5)
        every { repository.addUsedTokens(any(), any(), any()) } returns 0

        recorder.record(MEMBER_ID, 500)

        assertEquals(1.0, failureCount("no_mapping"))
        assertEquals(0.0, failureCount("error"))
    }

    /**
     * LLM 호출과 과금이 이미 끝난 뒤라 예외를 올리면 사용자 요청이 실패한다. 삼키되 사유를 갈라
     * 집계한다 - 매핑 누락은 로그인·백필을, 쓰기 실패는 DB 를 봐야 해서 대응이 다르다.
     */
    @Test
    fun `쓰기가 터져도 예외를 올리지 않고 실패로 집계한다`() {
        givenResetHour(5)
        every { repository.addUsedTokens(any(), any(), any()) } throws IllegalStateException("DB 연결 끊김")

        recorder.record(MEMBER_ID, 500)

        assertEquals(1.0, failureCount("error"))
        assertEquals(0.0, failureCount("no_mapping"))
    }

    /** 정책 조회가 실패해도 같다. 여기서 예외가 새면 생성 요청이 통째로 실패한다. */
    @Test
    fun `정책 조회가 실패해도 예외를 올리지 않는다`() {
        every { tokenPolicyService.current() } throws IllegalStateException("token_policy 행이 없다")

        recorder.record(MEMBER_ID, 500)

        assertEquals(1.0, failureCount("error"))
    }

    /** 사유별 카운터가 처음부터 등록돼 있어야 increase() 가 첫 발생을 놓치지 않는다. */
    @Test
    fun `실패 카운터는 발생 전에도 0 으로 등록돼 있다`() {
        assertEquals(0.0, failureCount("no_mapping"))
        assertEquals(0.0, failureCount("error"))
    }

    private fun givenResetHour(resetHour: Int) {
        every { tokenPolicyService.current() } returns TokenPolicy(dailyTokenLimit = 100_000, resetHour = resetHour)
    }

    private fun failureCount(reason: String): Double =
        meterRegistry
            .get("gamss.tokenlimit.quota.record.failure")
            .tag("reason", reason)
            .counter()
            .count()

    private companion object {
        const val MEMBER_ID = 42L
    }
}
