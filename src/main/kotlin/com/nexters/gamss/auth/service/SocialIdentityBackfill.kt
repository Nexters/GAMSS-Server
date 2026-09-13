package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.repository.SocialAccountRepository
import com.nexters.gamss.member.service.MemberSocialIdentityService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * 기동 1회, 아직 쿼터 주체가 없는 회원의 매핑을 `social_accounts` 에서 채운다.
 *
 * 매핑은 원래 로그인 때 생긴다. 그것만으로는 이 기능 배포 전에 가입해 아직 다시 로그인하지 않은
 * 회원이 매핑 없이 남고, 그 회원은 적립이 무효가 돼 한도가 그 사람에게만 꺼진다.
 *
 * 주체 키가 앱 환경변수의 HMAC 이라 마이그레이션으로는 못 하는 일이다. 이미 있는 매핑은
 * 건너뛰므로 재기동에 안전하다.
 */
@Component
class SocialIdentityBackfill(
    private val socialAccountRepository: SocialAccountRepository,
    private val memberSocialIdentityService: MemberSocialIdentityService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 쿼터 시드보다 먼저 돌아야 한다(com.nexters.gamss.tokenlimit.service.TokenQuotaBackfill). */
    @Order(ORDER)
    @EventListener(ApplicationReadyEvent::class)
    fun backfill() {
        // 회원 수만큼이라 한 번에 읽는다. 크게 늘면 페이지로 끊어야 기동이 헬스체크를 넘긴다.
        val accounts = socialAccountRepository.findAll()
        var mapped = 0
        accounts.forEach { account ->
            // 백필은 구멍을 메우는 일이지 기동을 좌우할 일이 아니다. 하나 실패해도 나머지는 채운다.
            runCatching { memberSocialIdentityService.ensureMapped(account.memberId, account.provider, account.providerId) }
                .onSuccess { mapped++ }
                .onFailure { log.error("쿼터 주체 매핑 백필 실패: memberId={}", account.memberId, it) }
        }
        log.info("쿼터 주체 매핑 백필 완료: 소셜 계정={}개, 처리={}개", accounts.size, mapped)
    }

    companion object {
        /** 쿼터 시드보다 앞선 값. 두 리스너의 순서가 이 상수 하나로 정해진다. */
        const val ORDER = 100
    }
}
