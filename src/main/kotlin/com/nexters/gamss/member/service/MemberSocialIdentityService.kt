package com.nexters.gamss.member.service

import com.nexters.gamss.member.domain.MemberSocialIdentity
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 회원과 쿼터 주체의 매핑을 관리한다. 쓰는 쪽은 로그인(auth), 읽는 쪽은 한도 판정(tokenlimit)이고
 * 둘 다 이미 member 에 의존하므로 여기 둔다.
 */
@Service
class MemberSocialIdentityService(
    private val repository: MemberSocialIdentityRepository,
    private val subjectKeyGenerator: SubjectKeyGenerator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이 회원의 매핑을 없을 때만 만든다. 로그인마다 불러도 되도록 멱등하다.
     *
     * 자기 트랜잭션에서 돌고 실패를 삼킨다. 부르는 쪽의 트랜잭션에서 유니크 위반이 터지면 그
     * 트랜잭션이 rollback-only 가 되어 로그인 자체가 실패하는데, 한도 집행용 부수 기록이 로그인을
     * 깨뜨릴 자격은 없다. 빠지면 다음 로그인과 기동 백필이 다시 채운다.
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
            // 동시 로그인 경합. 같은 신원이라 주체 키도 같으므로 먼저 커밋한 값을 그대로 쓴다.
            log.debug("쿼터 주체 매핑이 이미 있다: memberId={}", memberId, e)
        }
    }
}
