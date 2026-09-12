package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.auth.service.SocialIdentityBackfill
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
 * 카운터는 배포 순간 비어 있는데 사용량은 이미 있다. 그대로 두면 **배포가 그날 한도를 한 번 리셋해
 * 준다** - 막으려던 어뷰징을 배포가 대신 해주는 셈이다(#222).
 *
 * 구멍만 메운다. 이미 있는 쿼터 행은 덮어쓰지 않는다
 * ([TokenQuotaBackfillRepository.seedCurrentWindow]) - 매 기동 재계산하면 관측 테이블이 사실상
 * 진실의 원천으로 되돌아간다.
 *
 * 지금 구간만 채운다. 지난 구간은 이미 집행이 끝났고 보관 기간 정리 대상이다
 * ([TokenQuotaRetentionScheduler]).
 */
@Component
class TokenQuotaBackfill(
    private val tokenPolicyService: TokenPolicyService,
    private val repository: TokenQuotaBackfillRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 매핑 백필([SocialIdentityBackfill])보다 **뒤에** 돌아야 한다. 시드 쿼리가 매핑을 조인해 주체를
     * 찾으므로, 매핑이 아직 없으면 아무 행도 만들지 못한다.
     */
    @Order(ORDER)
    @EventListener(ApplicationReadyEvent::class)
    fun seed() {
        // 여기서 예외가 올라가면 기동이 실패한다. 시드는 한 번의 편의일 뿐이라 그럴 자격이 없다 -
        // 실패하면 그 구간의 사용량이 0 으로 시작하고, 다음 기동이 다시 시도한다.
        runCatching {
            val windowStart = TokenQuotaWindow.startOf(tokenPolicyService.current().resetHour)
            val seeded = repository.seedCurrentWindow(windowStart)
            log.info("쿼터 시드 완료: 구간 시작={}, 채운 주체={}개", windowStart, seeded)
        }.onFailure { log.error("쿼터 시드 실패(무시). 이 구간은 0 에서 시작한다", it) }
    }

    private companion object {
        /** 매핑 백필보다 뒤. 두 리스너의 순서를 이 관계 하나로 고정한다. */
        const val ORDER = SocialIdentityBackfill.ORDER + 1
    }
}
