package com.nexters.gamss.member.service

import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import org.springframework.stereotype.Service

/**
 * 회원과 쿼터 주체의 매핑을 관리한다. 쓰는 쪽은 로그인(auth), 읽는 쪽은 한도 판정(tokenlimit)이고
 * 둘 다 이미 member 에 의존하므로 여기 둔다.
 */
@Service
class MemberSocialIdentityService(
    private val repository: MemberSocialIdentityRepository,
    private val subjectKeyGenerator: SubjectKeyGenerator,
) {
    /**
     * 이 회원의 매핑을 보장한다. 로그인마다 불러도 되도록 멱등하다 - 이 기능 배포 전에 가입한
     * 회원도 다음 로그인에 매핑을 얻는다.
     *
     * 부르는 쪽 트랜잭션에 그대로 참여한다. 로그인이 뒤에서 실패하면 회원 행과 함께 롤백되므로
     * 없는 회원을 가리키는 매핑이 남지 않는다([MemberSocialIdentityRepository.upsert]).
     */
    fun ensureMapped(
        memberId: Long,
        provider: String,
        providerId: String,
    ) {
        repository.upsert(memberId, subjectKeyGenerator.generate(provider, providerId))
    }
}
