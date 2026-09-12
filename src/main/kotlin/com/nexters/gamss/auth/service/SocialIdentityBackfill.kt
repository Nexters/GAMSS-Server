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
 * 매핑은 원래 로그인 때 생긴다([SocialAccountService.resolveMember]). 그것만으로는 이 기능 배포
 * 전에 가입해 아직 다시 로그인하지 않은 회원이 매핑 없이 남고, 그 회원은 적립이 조용히 무효가 돼
 * **한도가 그 사람에게만 꺼진다**([com.nexters.gamss.tokenlimit.service.TokenQuotaRecorder] 가 ERROR
 * 로 올리지만 이미 지나간 생성이다).
 *
 * 마이그레이션으로 못 하는 일이다. 주체 키가 `HMAC(encryption.index-key, ...)` 이고 그 키는 앱
 * 환경변수에 있는데 MySQL 에는 HMAC 함수가 없다.
 *
 * **멱등하다.** 이미 있는 매핑은 건너뛰므로 재기동에 안전하다
 * ([MemberSocialIdentityService.ensureMapped]).
 */
@Component
class SocialIdentityBackfill(
    private val socialAccountRepository: SocialAccountRepository,
    private val memberSocialIdentityService: MemberSocialIdentityService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 쿼터 시드([com.nexters.gamss.tokenlimit.service.TokenQuotaBackfill])보다 **먼저** 돌아야 한다.
     * 시드 쿼리가 매핑을 조인해 주체를 찾으므로, 순서가 뒤집히면 시드가 아무 행도 못 만들고
     * 다음 기동까지 사용량이 0 으로 남는다.
     */
    @Order(ORDER)
    @EventListener(ApplicationReadyEvent::class)
    fun backfill() {
        // 이 테이블은 회원 수만큼이라 한 번에 읽는다. 지금 규모에서는 문제가 없지만, 회원이 크게
        // 늘면 페이지로 끊어야 한다 - 기동이 길어지면 헬스체크가 먼저 끊긴다.
        val accounts = socialAccountRepository.findAll()
        var mapped = 0
        accounts.forEach { account ->
            // 여기서 예외가 올라가면 기동이 실패한다. 백필은 구멍을 메우는 일이지 기동을 좌우할
            // 일이 아니라, 회원 하나가 실패해도 나머지는 계속 채운다.
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
