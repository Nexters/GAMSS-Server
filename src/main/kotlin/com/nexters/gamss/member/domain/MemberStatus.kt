package com.nexters.gamss.member.domain

/**
 * 회원 상태. 탈퇴는 소프트 삭제로 WITHDRAWN 전이한다.
 */
enum class MemberStatus {
    ACTIVE,
    WITHDRAWN,
}
