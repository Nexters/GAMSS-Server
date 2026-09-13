package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.auth.service.SocialIdentityBackfill
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.tokenlimit.domain.TokenQuotaWindow
import com.nexters.gamss.tokenlimit.repository.TokenQuotaBackfillRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * 기동 1회, 지금 구간의 쿼터를 `generation_log` 에서 시드한다.
 *
 * 카운터는 배포 순간 비어 있는데 사용량은 이미 있다. 그대로 두면 배포가 그날 한도를 한 번
 * 리셋해준다 - 막으려던 어뷰징을 배포가 대신 해주는 셈이다(#222).
 */
@Component
class TokenQuotaBackfill(
    private val tokenPolicyService: TokenPolicyService,
    private val repository: TokenQuotaBackfillRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 매핑 백필([SocialIdentityBackfill])보다 뒤에 돌아야 한다. 시드 쿼리가 매핑을 조인해 주체를
     * 찾으므로, 순서가 뒤집히면 아무 행도 만들지 못한다.
     */
    @Order(ORDER)
    @EventListener(ApplicationReadyEvent::class)
    fun seed() {
        // 시드는 한 번의 편의일 뿐이라 기동을 좌우할 자격이 없다. 실패하면 다음 기동이 다시 한다.
        runCatching {
            val windowStart = TokenQuotaWindow.startOf(tokenPolicyService.current().resetHour)
            val seeded = repository.seedCurrentWindow(windowStart, GenerationType.namesNotCountingTowardQuota())
            log.info("쿼터 시드 완료: 구간 시작={}, 채운 주체={}개", windowStart, seeded)
        }.onFailure { log.error("쿼터 시드 실패(무시). 이 구간은 0 에서 시작한다", it) }
    }

    private companion object {
        const val ORDER = SocialIdentityBackfill.ORDER + 1
    }
}
