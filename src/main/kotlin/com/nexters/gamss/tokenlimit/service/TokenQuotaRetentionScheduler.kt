package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.tokenlimit.domain.TokenQuotaWindow
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * 집행이 끝난 구간의 쿼터를 지운다. 한도는 하루짜리라 지난 구간은 더 쓸 데가 없고, 남겨두면
 * 주체별로 행이 무한히 쌓인다.
 *
 * 회원과 주체의 연결은 탈퇴 시점에 끊기므로 여기서 다루지 않는다
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
        runCatching {
            val purged = usageRepository.deleteByWindowStartBefore(usageCutoff())
            log.info("쿼터 보관기간 정리 완료: 사용량={}행", purged)
        }.onFailure { log.error("쿼터 보관기간 정리 실패(무시). 다음 회차가 다시 본다", it) }
    }

    /** 현재 구간 하나는 남긴다. 직전 구간까지 지우면 경계에서 방금 쓴 사용량이 사라질 여지가 있다. */
    private fun usageCutoff(): Instant = TokenQuotaWindow.startOf(tokenPolicyService.current().resetHour).minus(USAGE_RETENTION)

    private companion object {
        const val ZONE_ID = "Asia/Seoul"

        /** 리셋 시각(시드값 5)에서 떨어뜨린다. 경계 근처에서 돌면 정리와 판정이 겹친다. */
        const val CRON = "0 30 3 * * *"

        val USAGE_RETENTION: Duration = Duration.ofDays(1)
    }
}
