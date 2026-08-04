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
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 인증 · 소셜 로그인
    UNSUPPORTED_SOCIAL_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 제공자입니다."),
    INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 소셜 토큰입니다."),
    SOCIAL_AUTH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "소셜 인증 서버와 통신할 수 없습니다. 잠시 후 다시 시도해주세요."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰을 찾을 수 없습니다."),
    NOT_ADMIN(HttpStatus.FORBIDDEN, "백오피스 접근 권한이 없습니다."),

    // 회원
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "이미 탈퇴한 회원입니다."),
    WITHDRAWN_MEMBER(HttpStatus.FORBIDDEN, "탈퇴한 회원입니다."),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "사용할 수 없는 닉네임입니다."),

    // 대화
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    CONVERSATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 채팅방만 접근할 수 있습니다."),
    CONVERSATION_ALREADY_ENDED(HttpStatus.CONFLICT, "이미 종료된 채팅방입니다."),
    CONVERSATION_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 채팅방입니다."),
    CONVERSATION_ENDED(HttpStatus.CONFLICT, "종료된 채팅방에는 메시지를 추가할 수 없습니다."),
    CONVERSATION_NOT_ENDED(HttpStatus.CONFLICT, "종료된 채팅방에만 카드를 만들 수 있습니다."),
    INVALID_CONVERSATION_TITLE(HttpStatus.BAD_REQUEST, "사용할 수 없는 채팅방 제목입니다."),

    // 댓글 생성
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다."),
    INVALID_COMMENT_TARGET(HttpStatus.BAD_REQUEST, "일기(사용자) 메시지에만 댓글을 생성할 수 있습니다."),

    // 카드
    CARD_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 카드가 생성된 채팅방입니다."),
    CARD_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "카드 대사 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
    CARD_GENERATION_IN_PROGRESS(HttpStatus.CONFLICT, "카드를 생성하는 중입니다. 잠시 후 다시 시도해주세요."),
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다."),
    CARD_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 카드만 접근할 수 있습니다."),
    CARD_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 카드입니다."),

    // 토큰 상한
    DAILY_TOKEN_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘 사용할 수 있는 토큰을 모두 사용했습니다. 잠시 후 다시 시도해주세요."),

    // 백오피스 관리자
    ADMIN_ACCOUNT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 관리자입니다."),
    ADMIN_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "관리자를 찾을 수 없습니다."),
    CANNOT_REMOVE_SELF(HttpStatus.CONFLICT, "자기 자신은 관리자에서 삭제할 수 없습니다."),
    ;

    val code: String get() = name
}
