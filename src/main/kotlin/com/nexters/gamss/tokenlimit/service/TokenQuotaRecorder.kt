package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.tokenlimit.domain.TokenQuotaWindow
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
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
 */
@Service
class TokenQuotaRecorder(
    private val tokenPolicyService: TokenPolicyService,
    private val repository: TokenQuotaUsageRepository,
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
        if (!type.countsTowardQuota || usedTokens <= 0) {
            return
        }
        try {
            val windowStart = TokenQuotaWindow.startOf(tokenPolicyService.current().resetHour)
            val affected = repository.addUsedTokens(memberId, windowStart, usedTokens.toLong())
            if (affected == 0) {
                failureCounters.getValue(FailureReason.NO_MAPPING).increment()
                log.error("토큰 쿼터 적립 대상 없음(주체 매핑 누락): memberId={}, usedTokens={}", memberId, usedTokens)
            }
        } catch (e: Exception) {
            failureCounters.getValue(FailureReason.ERROR).increment()
            log.error("토큰 쿼터 적립 실패(무시): memberId={}, usedTokens={}", memberId, usedTokens, e)
        }
    }

    /** 대응이 갈리므로 사유를 나눈다. 매핑 누락은 로그인·백필을, 쓰기 실패는 DB 를 봐야 한다. */
    private enum class FailureReason(
        val tag: String,
    ) {
        NO_MAPPING("no_mapping"),
        ERROR("error"),
    }
}
