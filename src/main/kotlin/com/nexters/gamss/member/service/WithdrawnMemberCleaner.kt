package com.nexters.gamss.member.service

/**
 * 회원 탈퇴에 딸린 자원 정리 확장점. 회원 밖(다른 패키지)에 있는 자원의 정리는
 * 그 자원을 소유한 패키지가 이 인터페이스를 구현해 맡는다.
 *
 * 계약을 member 가 소유하고 구현을 바깥이 제공하는 형태(DIP)라, member 패키지는
 * 무엇이 정리되는지 몰라도 되고 의존은 `구현 패키지 -> member` 한 방향으로 유지된다.
 */
interface WithdrawnMemberCleaner {
    /** [memberId] 회원의 탈퇴로 더는 유지할 이유가 없어진 자원을 지운다. 탈퇴와 같은 트랜잭션에서 실행된다. */
    fun clean(memberId: Long)
}
