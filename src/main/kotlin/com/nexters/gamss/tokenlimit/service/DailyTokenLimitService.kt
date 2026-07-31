package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.monitoring.repository.GenerationLogRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 유저별 일일 토큰 상한을 판정한다. 상한 '적용 여부'는 코드 설정([enabled], prod에서만 true)이,
 * 상한 '값·리셋 시각'은 백오피스가 조절하는 [TokenPolicy]가 정한다. 상한이 꺼진 환경(dev/local)에서는
 * 항상 통과시킨다 — dev에는 제한이 없다는 요구사항 그대로다.
 *
 * '하루'의 경계는 자정이 아니라 정책의 리셋 시각(KST)이다: 예) resetHour=5면 오늘 05:00 ~ 내일 05:00 이
 * 한 구간이고, 그 구간의 소비 합이 상한에 닿으면 생성을 막는다(저장은 이미 끝났으므로 생성만 차단).
 */
@Service
class DailyTokenLimitService(
    private val tokenPolicyService: TokenPolicyService,
    private val generationLogRepository: GenerationLogRepository,
    @Value("\${gamss.token-limit.enabled:false}") private val enabled: Boolean,
) {
    /** 이 회원이 지금 생성해도 되는지(=상한 미도달). 상한이 꺼진 환경이면 항상 true. */
    fun isWithinLimit(memberId: Long): Boolean {
        if (!enabled) {
            return true
        }
        val policy = tokenPolicyService.current()
        val used = generationLogRepository.sumUsedTokensByMemberSince(memberId, windowStart(policy.resetHour))
        return used < policy.dailyTokenLimit
    }

    /** 현재 시점이 속한 일일 구간의 시작(KST 리셋 시각). 아직 오늘 리셋 시각 전이면 어제 리셋 시각이 시작이다. */
    private fun windowStart(resetHour: Int): Instant {
        val now = ZonedDateTime.now(ZONE)
        val todayReset = now.toLocalDate().atTime(resetHour, 0).atZone(ZONE)
        val start = if (now < todayReset) todayReset.minusDays(1) else todayReset
        return start.toInstant()
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
