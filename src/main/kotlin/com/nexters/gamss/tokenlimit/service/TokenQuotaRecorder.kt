package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.tokenlimit.domain.TokenQuotaWindow
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 생성 1건이 쓴 토큰을 그 회원의 쿼터 주체에 적립한다. 일일 한도 판정이 읽는 값을 만드는 유일한
 * 경로다([DailyTokenLimitService]).
 *
 * ### 관측 기록과 독립이다
 *
 * `generation_log` 기록([com.nexters.gamss.monitoring.service.GenerationLogRecorder])과 같은 자리에서
 * 같은 값으로 불리지만 한 트랜잭션으로 묶지 않는다. 그쪽은 비용 분석용이라 실패를 warn 으로 삼키는
 * 계약인데, 여기에 묶으면 관측 기록 실패가 한도 집행을 함께 끌고 내려간다. 대신 두 값이 정확히
 * 일치한다는 보장도 없어진다 - 서로 다른 소비자를 가진 서로 다른 기록이라 그렇게 둔다.
 *
 * ### 실패를 삼키지만 조용히는 아니다
 *
 * 적립이 실패하면 그만큼 한도가 느슨해진다. 그렇다고 예외를 올리면 **LLM 호출과 과금이 이미 끝난 뒤에**
 * 사용자 요청을 실패시키는 셈이라 더 나쁘다. 그래서 삼키되 ERROR 와 전용 카운터로 올린다 - 관측 기록의
 * warn 보다 강하게 두는 것은 이쪽 실패에 비즈니스 결과가 따르기 때문이다.
 *
 * ### 실패한 시도의 토큰도 적립한다
 *
 * 부르는 쪽이 넘기는 값은 재시도까지 합산한 실제 과금이다
 * ([com.nexters.gamss.llm.generation.TokenUsageAccumulator]). 지금까지 `generation_log` 합산도 실패 행을
 * 포함했으므로 동작이 유지된다.
 */
@Service
class TokenQuotaRecorder(
    private val tokenPolicyService: TokenPolicyService,
    private val repository: TokenQuotaUsageRepository,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 실패 사유별 카운터를 미리 0 으로 등록한다. 처음 발생할 때 만들면 그 시계열이 '없다가 생긴' 것이
     * 되고, increase() 는 구간의 첫 값을 기준으로 삼아 그 증가를 세지 않는다
     * ([com.nexters.gamss.card.service.DailyAutoCardScheduler] 와 같은 이유).
     */
    private val failureCounters: Map<FailureReason, Counter> =
        FailureReason.entries.associateWith { reason ->
            Counter
                .builder("gamss.tokenlimit.quota.record.failure")
                .description("토큰 쿼터 적립에 실패한 생성 건수(사유별)")
                .tag("reason", reason.tag)
                .register(meterRegistry)
        }

    /** [memberId] 의 쿼터에 [usedTokens] 를 더한다. 0 이하면 적립할 것이 없어 그냥 지나간다. */
    fun record(
        memberId: Long,
        usedTokens: Int,
    ) {
        if (usedTokens <= 0) {
            return
        }
        try {
            val windowStart = TokenQuotaWindow.startOf(tokenPolicyService.current().resetHour)
            val affected = repository.addUsedTokens(memberId, windowStart, usedTokens.toLong())
            if (affected == 0) {
                // 주체 매핑이 없어 적립할 행을 못 찾았다. 그 회원은 한도가 사실상 꺼진 상태이므로
                // 조용히 넘기면 안 된다 - 로그인 시 매핑과 기동 백필 둘 다 실패했다는 뜻이다.
                failureCounters.getValue(FailureReason.NO_MAPPING).increment()
                log.error("토큰 쿼터 적립 대상 없음(주체 매핑 누락): memberId={}, usedTokens={}", memberId, usedTokens)
            }
        } catch (e: Exception) {
            failureCounters.getValue(FailureReason.ERROR).increment()
            log.error("토큰 쿼터 적립 실패(무시): memberId={}, usedTokens={}", memberId, usedTokens, e)
        }
    }

    /** 적립이 실패하는 두 가지. 대응이 갈리므로 메트릭에서도 갈라 둔다. */
    private enum class FailureReason(
        val tag: String,
    ) {
        /** 쿼터 주체 매핑이 없다. 로그인 경로나 백필을 봐야 한다. */
        NO_MAPPING("no_mapping"),

        /** 쓰기 자체가 터졌다. DB 를 봐야 한다. */
        ERROR("error"),
    }
}
