package com.nexters.gamss.global.exception

import org.springframework.http.HttpStatus

/**
 * 도메인 예외의 에러 코드. 새 에러는 여기에 상수만 추가한다(OCP).
 */
enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 인증 · 소셜 로그인
    UNSUPPORTED_SOCIAL_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 제공자입니다."),
    INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 소셜 토큰입니다."),
    SOCIAL_AUTH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "소셜 인증 서버와 통신할 수 없습니다. 잠시 후 다시 시도해주세요."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰을 찾을 수 없습니다."),

    // 회원
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "이미 탈퇴한 회원입니다."),
    WITHDRAWN_MEMBER(HttpStatus.FORBIDDEN, "탈퇴한 회원입니다."),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "사용할 수 없는 닉네임입니다."),
    ;

    val code: String get() = name
}
