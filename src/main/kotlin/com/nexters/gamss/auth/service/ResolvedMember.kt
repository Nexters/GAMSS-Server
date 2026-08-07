package com.nexters.gamss.auth.service

import com.nexters.gamss.member.domain.Member

/**
 * 소셜 계정으로 확보한 회원. isNewMember 는 이번 로그인에서 새로 가입했는지 여부다.
 */
data class ResolvedMember(
    val member: Member,
    val isNewMember: Boolean,
)
