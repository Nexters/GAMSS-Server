package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.tokenlimit.domain.TokenQuotaWindow
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 유저별 일일 토큰 상한을 판정한다. 상한 '적용 여부'는 코드 설정([enabled], 배포 환경 dev/prod에서 true)이,
 * 상한 '값·리셋 시각'은 백오피스가 조절하는 [TokenPolicy]가 정한다. 상한이 꺼진 환경(local)에서는
 * 항상 통과시킨다. dev도 상한을 켜는 이유는 막기 위해서가 아니라 클라이언트가 사용량 응답(dailyLimit)을
 * 실제 값으로 받아보게 하기 위해서다 — 막히지 않도록 dev의 상한 값은 백오피스에서 크게 잡아둔다.
 *
 * '하루'의 경계는 자정이 아니라 정책의 리셋 시각(KST)이다: 예) resetHour=5면 오늘 05:00 ~ 내일 05:00 이
 * 한 구간이고, 그 구간의 소비 합이 상한에 닿으면 생성을 막는다(저장은 이미 끝났으므로 생성만 차단).
 *
 * ### 사용량은 회원이 아니라 쿼터 주체에 귀속된다
 *
 * 예전에는 `generation_log` 를 `member_id` 로 합산했는데, 그러면 탈퇴 후 재가입으로 한도가 리셋된다 -
 * 재가입은 새 회원 행을 받고 옛 사용량은 옛 id 에 남는데 둘을 이어줄 식별자가 없었기 때문이다(#222).
 * 지금은 소셜 신원에서 나온 주체에 적립하므로([TokenQuotaRecorder]) 재가입해도 같은 값을 본다.
 *
 * 그래서 이 서비스는 관측용 테이블(`generation_log`)을 더 이상 읽지 않는다. 한도는 비즈니스 규칙이고
 * 그 판정 근거가 관측 기록에 얹혀 있을 이유가 없다. 대신 두 값이 정확히 일치한다는 보장은 없다
 * ([com.nexters.gamss.tokenlimit.domain.TokenQuotaUsage]).
 */
@Service
class DailyTokenLimitService(
    private val tokenPolicyService: TokenPolicyService,
    private val tokenQuotaUsageRepository: TokenQuotaUsageRepository,
    @Value("\${gamss.token-limit.enabled:false}") private val enabled: Boolean,
) {
    /** 이 회원이 지금 생성해도 되는지(=상한 미도달). 상한이 꺼진 환경이면 항상 true. */
    fun isWithinLimit(memberId: Long): Boolean {
        if (!enabled) {
            return true
        }
        val policy = tokenPolicyService.current()
        return usedTokens(memberId, policy.resetHour) < policy.dailyTokenLimit
    }

    /**
     * 이 회원의 오늘(정책 리셋 시각 기준) 토큰 사용량. 상한이 꺼진 환경([enabled]=false)이면
     * [TokenUsage.dailyLimit]이 null(무제한)이고 [TokenUsage.exceeded]는 항상 false다.
     */
    fun usageFor(memberId: Long): TokenUsage {
        val policy = tokenPolicyService.current()
        val used = usedTokens(memberId, policy.resetHour)
        if (!enabled) {
            return TokenUsage(usedTokens = used, dailyLimit = null, exceeded = false)
        }
        return TokenUsage(usedTokens = used, dailyLimit = policy.dailyTokenLimit, exceeded = used >= policy.dailyTokenLimit)
    }

    /**
     * 이 회원의 현재 구간 사용량. 주체 매핑이 없으면 0 이다 - 그 회원은 적립도 안 되고 있어
     * ([TokenQuotaRecorder] 가 ERROR 로 올린다) 한도가 사실상 꺼진 상태다.
     *
     * 구간 계산은 적립 쪽과 같은 것을 쓴다([TokenQuotaWindow]). 각자 계산하면 경계 근처에서 방금
     * 적립한 값이 다른 구간으로 들어가 판정에서 빠진다.
     */
    private fun usedTokens(
        memberId: Long,
        resetHour: Int,
    ): Long = tokenQuotaUsageRepository.findUsedTokens(memberId, TokenQuotaWindow.startOf(resetHour))
}
