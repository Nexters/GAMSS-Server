package com.nexters.gamss.global.security

/**
 * 인증된 요청 주체. 컨트롤러에서 @AuthenticationPrincipal 로 회원 식별자를 받는다.
 */
data class AuthPrincipal(
    val memberId: Long,
)
