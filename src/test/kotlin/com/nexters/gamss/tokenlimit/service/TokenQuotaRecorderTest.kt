package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.global.retry.ConflictRetry
import com.nexters.gamss.member.service.MemberSocialIdentityService
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.tokenlimit.domain.TokenPolicy
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import com.nexters.gamss.wallet.service.TokenLedgerConflictException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

/** 적립이 조용히 실패하면 그 회원의 한도가 꺼진다. "삼키되 드러낸다"가 이 클래스의 계약이다. */
class TokenQuotaRecorderTest {
    private val tokenPolicyService = mockk<TokenPolicyService>()
    private val repository = mockk<TokenQuotaUsageRepository>()
    private val meterRegistry = SimpleMeterRegistry()
    private val identityService = mockk<MemberSocialIdentityService>()
    private val settlement = mockk<TokenQuotaSettlement>()
    private val recorder = recorder(enabled = true)

    private fun recorder(enabled: Boolean) =
        TokenQuotaRecorder(tokenPolicyService, repository, identityService, settlement, ConflictRetry(), enabled, meterRegistry)

    @Test
    fun `사용한 토큰을 주체에 적립한다`() {
        givenResetHour(5)
        every { repository.addUsedTokens(any(), any(), any()) } returns 1

        recorder.record(GenerationType.COMMENT, MEMBER_ID, 1_200)

        verify { repository.addUsedTokens(MEMBER_ID, any(), 1_200L) }
    }

    /**
     * 합산 대상 판단이 호출부가 아니라 종류에 있다는 계약. 호출부가 판단하면 정책이 호출부 수만큼
     * 흩어져, 백필 쿼리의 제외 목록과 어긋났을 때 재기동 시점에 따라 사용량이 달라진다.
     */
    @Test
    fun `한도에 합산하지 않는 종류는 적립하지 않는다`() {
        GenerationType.entries.filterNot { it.countsTowardQuota }.forEach { type ->
            recorder.record(type, MEMBER_ID, 1_000)
        }

        verify(exactly = 0) { repository.addUsedTokens(any(), any(), any()) }
    }

    /** 백필의 제외 목록이 적립 기준과 같은 곳에서 나와야 두 경로가 어긋나지 않는다. */
    @Test
    fun `백필 제외 목록은 합산하지 않는 종류와 같다`() {
        assertEquals(
            GenerationType.entries.filterNot { it.countsTowardQuota }.map { it.name },
            GenerationType.namesNotCountingTowardQuota(),
        )
    }

    @Test
    fun `토큰을 쓰지 않았으면 적립하지 않는다`() {
        recorder.record(GenerationType.COMMENT, MEMBER_ID, 0)

        verify(exactly = 0) { repository.addUsedTokens(any(), any(), any()) }
    }

    /** 매핑이 없으면 0 행이 된다. 예외가 아니라 조용한 무효과라 드러내야 한다. */
    @Test
    fun `주체 매핑이 없으면 실패로 집계한다`() {
        givenResetHour(5)
        every { repository.addUsedTokens(any(), any(), any()) } returns 0

        recorder.record(GenerationType.COMMENT, MEMBER_ID, 500)

        assertEquals(1.0, failureCount("no_mapping"))
        assertEquals(0.0, failureCount("error"))
    }

    /** 과금이 이미 끝난 뒤라 예외를 올리면 사용자 요청이 실패한다. */
    @Test
    fun `쓰기가 터져도 예외를 올리지 않고 실패로 집계한다`() {
        givenResetHour(5)
        every { repository.addUsedTokens(any(), any(), any()) } throws IllegalStateException("DB 연결 끊김")

        recorder.record(GenerationType.COMMENT, MEMBER_ID, 500)

        assertEquals(1.0, failureCount("error"))
        assertEquals(0.0, failureCount("no_mapping"))
    }

    @Test
    fun `정책 조회가 실패해도 예외를 올리지 않는다`() {
        every { tokenPolicyService.current() } throws IllegalStateException("token_policy 행이 없다")

        recorder.record(GenerationType.COMMENT, MEMBER_ID, 500)

        assertEquals(1.0, failureCount("error"))
    }

    /** increase() 가 첫 발생을 놓치지 않으려면 처음부터 등록돼 있어야 한다. */
    @Test
    fun `실패 카운터는 발생 전에도 0 으로 등록돼 있다`() {
        assertEquals(0.0, failureCount("no_mapping"))
        assertEquals(0.0, failureCount("error"))
        assertEquals(0.0, failureCount("paid_error"))
    }

    @Test
    fun `응답을 받은 생성은 정책의 한도로 적립과 차감을 맡긴다`() {
        givenResetHour(5)
        givenSubject()
        every { settlement.settle(any(), any(), any(), any(), any()) } returns true

        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 1_200)

        verify(exactly = 1) { settlement.settle(MEMBER_ID, SUBJECT, any(), 100_000, 1_200) }
        verify(exactly = 0) { repository.addUsedTokens(any(), any(), any()) }
    }

    /** 응답을 받지 못한 생성의 경로다. 여기서 차감하면 돈을 낸 토큰을 결과 없이 잃는다. */
    @Test
    fun `record 는 유료 토큰을 깎지 않는다`() {
        givenResetHour(5)
        every { repository.addUsedTokens(any(), any(), any()) } returns 1

        recorder.record(GenerationType.COMMENT, MEMBER_ID, 1_200)

        verify(exactly = 0) { settlement.settle(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `상한이 꺼진 환경에서는 적립만 하고 유료 토큰을 깎지 않는다`() {
        givenResetHour(5)
        every { repository.addUsedTokens(any(), any(), any()) } returns 1

        recorder(enabled = false).recordDelivered(GenerationType.COMMENT, MEMBER_ID, 1_200)

        verify { repository.addUsedTokens(MEMBER_ID, any(), 1_200L) }
        verify(exactly = 0) { settlement.settle(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `한도에 합산하지 않는 종류나 쓴 토큰이 없는 생성은 응답을 받아도 처리하지 않는다`() {
        GenerationType.entries.filterNot { it.countsTowardQuota }.forEach { type ->
            recorder.recordDelivered(type, MEMBER_ID, 1_000)
        }
        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 0)

        verify(exactly = 0) { settlement.settle(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { repository.addUsedTokens(any(), any(), any()) }
    }

    @Test
    fun `응답을 받은 생성의 주체 매핑이 없으면 실패로 집계한다`() {
        givenResetHour(5)
        every { identityService.subjectKeyOf(MEMBER_ID) } returns null

        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 500)

        assertEquals(1.0, failureCount("no_mapping"))
        verify(exactly = 0) { settlement.settle(any(), any(), any(), any(), any()) }
    }

    /** 주체를 찾은 뒤 탈퇴로 매핑이 지워지면 적립이 0 행이 된다. */
    @Test
    fun `적립 시점에 매핑이 사라져도 실패로 집계한다`() {
        givenResetHour(5)
        givenSubject()
        every { settlement.settle(any(), any(), any(), any(), any()) } returns false

        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 500)

        assertEquals(1.0, failureCount("no_mapping"))
        assertEquals(0.0, failureCount("paid_error"))
    }

    @Test
    fun `유료 차감이 실패하면 적립만 다시 하고 paid_error 로 집계한다`() {
        givenResetHour(5)
        givenSubject()
        every { settlement.settle(any(), any(), any(), any(), any()) } throws IllegalStateException("지갑 장애")
        every { repository.addUsedTokens(any(), any(), any()) } returns 1

        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 500)

        verify(exactly = 1) { repository.addUsedTokens(MEMBER_ID, any(), 500L) }
        assertEquals(1.0, failureCount("paid_error"))
        assertEquals(0.0, failureCount("error"))
    }

    /** 적립조차 못 했으면 유료만 빠진 상태가 아니라 한도가 샌 상태라 볼 곳이 다르다. */
    @Test
    fun `다시 한 적립까지 실패하면 error 로 집계하고 예외를 올리지 않는다`() {
        givenResetHour(5)
        givenSubject()
        every { settlement.settle(any(), any(), any(), any(), any()) } throws IllegalStateException("DB 연결 끊김")
        every { repository.addUsedTokens(any(), any(), any()) } throws IllegalStateException("DB 연결 끊김")

        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 500)

        assertEquals(1.0, failureCount("error"))
        assertEquals(0.0, failureCount("paid_error"))
    }

    @Test
    fun `응답을 받은 생성은 정책 조회가 실패해도 예외를 올리지 않는다`() {
        every { tokenPolicyService.current() } throws IllegalStateException("token_policy 행이 없다")

        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 500)

        assertEquals(1.0, failureCount("error"))
    }

    @Test
    fun `지갑 경합은 트랜잭션 밖에서 다시 실행해 끝낸다`() {
        givenResetHour(5)
        givenSubject()
        every { settlement.settle(any(), any(), any(), any(), any()) } throws
            TokenLedgerConflictException(null, IllegalStateException("데드락")) andThen true

        recorder.recordDelivered(GenerationType.COMMENT, MEMBER_ID, 500)

        verify(exactly = 2) { settlement.settle(any(), any(), any(), any(), any()) }
        assertEquals(0.0, failureCount("paid_error"))
    }

    private fun givenSubject() {
        every { identityService.subjectKeyOf(MEMBER_ID) } returns SUBJECT
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
        val SUBJECT = "a".repeat(64)
    }
}
