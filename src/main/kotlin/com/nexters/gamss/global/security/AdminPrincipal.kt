package com.nexters.gamss.global.security

/**
 * 인증된 백오피스 관리자 주체. 관리자는 회원(Member)과 무관한 내부 운영자이므로
 * 식별자로 이메일을 쓴다. 컨트롤러에서 @AuthenticationPrincipal 로 받는다.
 */
data class AdminPrincipal(
    val email: String,
)
