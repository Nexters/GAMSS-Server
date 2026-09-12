package com.nexters.gamss.member.service

import com.nexters.gamss.member.domain.MemberSocialIdentity
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 회원과 쿼터 주체의 매핑을 관리한다([MemberSocialIdentity]).
 *
 * 쓰는 쪽은 로그인(auth), 읽는 쪽은 한도 판정(tokenlimit)이다. 둘 다 이미 member 에 의존하므로
 * 여기 두면 의존 방향이 꼬이지 않는다.
 */
@Service
class MemberSocialIdentityService(
    private val repository: MemberSocialIdentityRepository,
    private val subjectKeyGenerator: SubjectKeyGenerator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이 회원의 매핑을 없을 때만 만든다. 로그인마다 불러도 되도록 멱등하다 - 이 기능 배포 전에
     * 가입한 회원도 다음 로그인에 매핑을 얻는다.
     *
     * **자기 트랜잭션에서 돈다.** 중복 삽입이 유니크 위반으로 걸릴 수 있는데, 부르는 쪽
     * ([com.nexters.gamss.auth.service.SocialAccountService.resolveMember])의 트랜잭션 안에서 터지면
     * 그 트랜잭션이 rollback-only 로 표시돼 로그인 자체가 실패한다. 매핑은 한도 집행용 부수 기록이라
     * 로그인을 깨뜨릴 자격이 없다.
     *
     * **실패를 삼킨다.** 매핑이 없으면 그 회원의 사용량이 집계되지 않아 한도가 느슨해지지만,
     * 그렇다고 로그인을 막으면 훨씬 나쁘다. 대신 다음 로그인에 다시 시도되고 기동 백필도 메운다
     * ([com.nexters.gamss.tokenlimit.service.TokenQuotaBackfill]).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun ensureMapped(
        memberId: Long,
        provider: String,
        providerId: String,
    ) {
        if (repository.existsByMemberId(memberId)) {
            return
        }
        try {
            repository.save(MemberSocialIdentity(memberId, subjectKeyGenerator.generate(provider, providerId)))
        } catch (e: DataIntegrityViolationException) {
            // 동시 로그인 두 건이 같은 회원의 매핑을 만들려 한 경우다. 먼저 커밋한 쪽의 값이 남아
            // 있으므로 할 일이 없다 - 두 요청이 같은 신원에서 왔으니 주체 키도 같다.
            log.debug("쿼터 주체 매핑이 이미 있다: memberId={}", memberId, e)
        }
    }
}
