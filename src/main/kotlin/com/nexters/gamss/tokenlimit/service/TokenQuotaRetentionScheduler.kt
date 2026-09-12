package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.tokenlimit.domain.TokenQuotaWindow
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * 집행이 끝난 쿼터와, 탈퇴자의 주체 연결을 끊는다.
 *
 * ### 왜 지우는가
 *
 * [com.nexters.gamss.member.domain.MemberSocialIdentity] 는 탈퇴 후에도 남아야 한다 - 그것이 재가입
 * 한도 리셋을 막는 고리다(#222). 그런데 영구히 두면 **탈퇴한 사람을 과거 행적에 언제든 다시 연결할
 * 수 있는 상태**가 된다. 탈퇴 시 개인 식별정보를 지우는 원칙([com.nexters.gamss.member.domain.Member])
 * 과 부딪히는 지점이고, 해시라 역산이 안 된다는 것은 답이 아니다 - 문제는 복원 가능성이 아니라
 * 연결 가능성이다.
 *
 * 막으려는 것은 **하루** 한도다. 한 구간이 지나면 연결이 없어도 어차피 한도가 리셋될 시점이라,
 * **기능을 하나도 잃지 않고** 연결만 끊을 수 있다. 목적에 필요한 기간만 보관한다.
 *
 * ### 활성 회원은 건드리지 않는다
 *
 * 활성 회원의 매핑을 지우면 그 회원의 적립이 무효가 되어 한도가 사실상 꺼진다. 탈퇴한 회원만,
 * 그것도 [IDENTITY_RETENTION] 이 지난 뒤에 끊는다.
 */
@Component
class TokenQuotaRetentionScheduler(
    private val tokenPolicyService: TokenPolicyService,
    private val usageRepository: TokenQuotaUsageRepository,
    private val identityRepository: MemberSocialIdentityRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = CRON, zone = ZONE_ID)
    fun purge() {
        // 이 정리가 실패해도 서비스는 돌아간다. 다음 회차가 다시 본다 - 지우지 못한 행이 늘 뿐이다.
        runCatching {
            val now = Instant.now()
            val purgedUsage = usageRepository.deleteByWindowStartBefore(usageCutoff())
            val purgedIdentities = identityRepository.deleteWithdrawnBefore(now.minus(IDENTITY_RETENTION))
            log.info("쿼터 보관기간 정리 완료: 사용량={}행, 탈퇴자 주체 매핑={}행", purgedUsage, purgedIdentities)
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

        /**
         * 탈퇴 후 주체 연결을 남기는 기간. 한 구간(24시간)에 여유를 더한 값이다 - 정리가 하루 한 번만
         * 돌므로 딱 24시간으로 두면 회차 사이에 걸린 탈퇴가 한 구간을 다 채우기 전에 끊길 수 있다.
         */
        val IDENTITY_RETENTION: Duration = Duration.ofDays(2)
    }
}
