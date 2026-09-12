package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.tokenlimit.domain.TokenQuotaWindow
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * 집행이 끝난 구간의 쿼터를 지운다.
 *
 * 한도는 하루짜리라 지난 구간의 사용량은 더 쓸 데가 없다. 남겨두면 주체별로 행이 무한히 쌓인다 -
 * 특히 탈퇴·재가입을 반복하는 쪽이 만드는 행은 우리가 막으려는 그 행위의 부산물이다.
 *
 * 회원과 주체의 연결은 여기서 다루지 않는다. 탈퇴 시점에 다른 자원과 함께 끊기 때문이다
 * ([com.nexters.gamss.member.service.MemberSocialIdentityCleaner]).
 */
@Component
class TokenQuotaRetentionScheduler(
    private val tokenPolicyService: TokenPolicyService,
    private val usageRepository: TokenQuotaUsageRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = CRON, zone = ZONE_ID)
    fun purge() {
        // 이 정리가 실패해도 서비스는 돌아간다. 다음 회차가 다시 본다 - 지우지 못한 행이 늘 뿐이다.
        runCatching {
            val purged = usageRepository.deleteByWindowStartBefore(usageCutoff())
            log.info("쿼터 보관기간 정리 완료: 사용량={}행", purged)
        }.onFailure { log.error("쿼터 보관기간 정리 실패(무시). 다음 회차가 다시 본다", it) }
    }

    /**
     * 이 시각 이전 구간의 사용량을 지운다.
     *
     * 현재 구간 하나를 남긴다. 직전 구간까지 지우면 리셋 시각 경계에서 방금 쓴 사용량이 사라질
     * 여지가 있고, 장애 조사에서 "어제 얼마 썼는지"를 볼 수 있는 폭도 없어진다.
     */
    private fun usageCutoff(): Instant {
        val resetHour = tokenPolicyService.current().resetHour
        return TokenQuotaWindow.startOf(resetHour).minus(USAGE_RETENTION)
    }

    private companion object {
        const val ZONE_ID = "Asia/Seoul"

        /**
         * 하루 경계에서 한참 떨어진 시각에 돈다. 리셋 시각(`token_policy.reset_hour`, 시드값 5)
         * 근처에서 돌면 정리와 판정이 같은 경계를 두고 겹친다.
         */
        const val CRON = "0 30 3 * * *"

        /** 사용량은 현재 구간 위로 하루를 더 남긴다. */
        val USAGE_RETENTION: Duration = Duration.ofDays(1)
    }
}
