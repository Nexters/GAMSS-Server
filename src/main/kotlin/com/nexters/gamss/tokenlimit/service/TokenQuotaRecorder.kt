package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.global.retry.ConflictRetry
import com.nexters.gamss.member.service.MemberSocialIdentityService
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.tokenlimit.domain.TokenQuotaWindow
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 생성 1건이 쓴 토큰을 그 회원의 쿼터 주체에 적립한다. [DailyTokenLimitService] 가 읽는 값을
 * 만드는 유일한 경로다.
 *
 * `generation_log` 기록과 같은 자리에서 같은 값으로 불리지만 한 트랜잭션으로 묶지 않는다 -
 * 그쪽은 실패를 warn 으로 삼키는 계약이라, 묶으면 관측 기록 실패가 한도 집행을 끌고 내려간다.
 *
 * 적립 실패는 삼키되 ERROR 와 카운터로 올린다. LLM 호출과 과금이 이미 끝난 뒤라 예외를 올리면
 * 사용자 요청이 실패하는데, 조용히 넘기면 그 회원만 한도가 꺼진다.
 *
 * 응답을 받지 못한 생성은 [record] 로 일일 사용량만 적립하고, 받은 생성은 [recordDelivered] 로 일일 한도를
 * 넘긴 만큼 유료 토큰까지 깎는다(#239). 돈을 낸 토큰을 결과 없이 잃지 않게 하려는 구분이다.
 */
@Service
class TokenQuotaRecorder(
    private val tokenPolicyService: TokenPolicyService,
    private val repository: TokenQuotaUsageRepository,
    private val identityService: MemberSocialIdentityService,
    private val settlement: TokenQuotaSettlement,
    private val conflictRetry: ConflictRetry,
    @Value("\${gamss.token-limit.enabled:false}") private val enabled: Boolean,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 사유별 카운터를 미리 0 으로 등록한다. 처음 발생할 때 만들면 increase() 가 그 증가를 놓친다. */
    private val failureCounters: Map<FailureReason, Counter> =
        FailureReason.entries.associateWith { reason ->
            Counter
                .builder("gamss.tokenlimit.quota.record.failure")
                .description("토큰 쿼터 적립에 실패한 생성 건수(사유별)")
                .tag("reason", reason.tag)
                .register(meterRegistry)
        }

    /**
     * 한도에 합산되는 종류인지는 [GenerationType.countsTowardQuota] 가 정한다 - 호출부가 부를지
     * 말지로 정하면 정책이 호출부 수만큼 흩어져, 한 곳을 빠뜨렸을 때 조용히 어긋난다.
     */
    fun record(
        type: GenerationType,
        memberId: Long,
        usedTokens: Int,
    ) {
        if (!countsTowardQuota(type, usedTokens)) {
            return
        }
        addUsage(memberId, usedTokens)
    }

    /**
     * 응답이 회원에게 전달된 생성을 적립하고, 일일 한도를 넘긴 만큼 유료 토큰을 깎는다. 상한이 꺼진
     * 환경에서는 한도가 없으므로 깎지 않는다.
     *
     * 적립과 차감은 한 트랜잭션이라 실패하면 적립까지 롤백된다. 일일 한도가 새지 않도록 적립만 한 번 더
     * 시도하고, 성공하면 유료 차감만 빠진 상태가 된다.
     */
    fun recordDelivered(
        type: GenerationType,
        memberId: Long,
        usedTokens: Int,
    ) {
        if (!countsTowardQuota(type, usedTokens)) {
            return
        }
        if (!enabled) {
            addUsage(memberId, usedTokens)
            return
        }
        try {
            if (!settle(memberId, usedTokens.toLong())) {
                countNoMapping(memberId, usedTokens)
            }
        } catch (e: Exception) {
            log.error("유료 토큰 차감 실패. 적립만 다시 시도한다: memberId={}, usedTokens={}", memberId, usedTokens, e)
            if (addUsage(memberId, usedTokens)) {
                failureCounters.getValue(FailureReason.PAID_ERROR).increment()
            }
        }
    }

    /**
     * 주체는 트랜잭션 밖에서 찾는다. 안에서 먼저 읽으면 그 시점으로 스냅샷이 잡혀, 잠금을 기다리는 사이 첫
     * 구매로 생긴 지갑을 없는 것으로 보고 차감을 건너뛴다.
     *
     * 지갑 경합의 재시도는 #238 의 규칙대로 가장 바깥인 여기서 감싼다. 실패하면 트랜잭션 전체가 롤백되므로
     * 다시 실행해도 사용량이 두 번 쌓이지 않는다. 그 밖의 실패는 재시도하지 않고 [recordDelivered] 가 처리한다.
     */
    private fun settle(
        memberId: Long,
        delta: Long,
    ): Boolean {
        val policy = tokenPolicyService.current()
        val subjectKey = identityService.subjectKeyOf(memberId) ?: return false
        val windowStart = TokenQuotaWindow.startOf(policy.resetHour)
        return conflictRetry.execute {
            settlement.settle(memberId, subjectKey, windowStart, policy.dailyTokenLimit, delta)
        }
    }

    /** @return 사용량이 실제로 쌓였으면 true */
    private fun addUsage(
        memberId: Long,
        usedTokens: Int,
    ): Boolean {
        try {
            val windowStart = TokenQuotaWindow.startOf(tokenPolicyService.current().resetHour)
            if (repository.addUsedTokens(memberId, windowStart, usedTokens.toLong()) == 0) {
                countNoMapping(memberId, usedTokens)
                return false
            }
            return true
        } catch (e: Exception) {
            failureCounters.getValue(FailureReason.ERROR).increment()
            log.error("토큰 쿼터 적립 실패(무시): memberId={}, usedTokens={}", memberId, usedTokens, e)
            return false
        }
    }

    private fun countsTowardQuota(
        type: GenerationType,
        usedTokens: Int,
    ): Boolean = type.countsTowardQuota && usedTokens > 0

    private fun countNoMapping(
        memberId: Long,
        usedTokens: Int,
    ) {
        failureCounters.getValue(FailureReason.NO_MAPPING).increment()
        log.error("토큰 쿼터 적립 대상 없음(주체 매핑 누락): memberId={}, usedTokens={}", memberId, usedTokens)
    }

    /**
     * 대응이 갈리므로 사유를 나눈다. 매핑 누락은 로그인·백필을, 쓰기 실패는 DB 를 봐야 한다. 유료 차감
     * 실패는 일일 사용량은 쌓였고 유료 잔액만 덜 깎인 상태라, 쌓이지 못한 [ERROR] 와 구분한다.
     */
    private enum class FailureReason(
        val tag: String,
    ) {
        NO_MAPPING("no_mapping"),
        ERROR("error"),
        PAID_ERROR("paid_error"),
    }
}
