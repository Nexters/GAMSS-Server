package com.nexters.gamss.global.exception

/**
 * 도메인 예외의 에러 코드. 새 에러는 여기에 상수만 추가한다(OCP).
 *
 * HTTP 상태 코드를 들지 않는다. 이 enum 은 도메인이 그대로 참조하는데(닉네임·제목 검증 등),
 * 상태 코드를 들면 그 도메인 규칙들이 전부 스프링 웹 타입에 묶인다. 성격만 [ErrorKind]로 말하고
 * 상태 코드로의 번역은 웹 계층이 한다([com.nexters.gamss.global.web.httpStatusOf]).
 */
enum class ErrorCode(
    val kind: ErrorKind,
    val message: String,
) {
    // 공통
    INVALID_INPUT(ErrorKind.INVALID_INPUT, "잘못된 요청입니다."),
    UNAUTHORIZED(ErrorKind.UNAUTHENTICATED, "인증이 필요합니다."),
    ACCESS_DENIED(ErrorKind.PERMISSION_DENIED, "접근 권한이 없습니다."),
    INTERNAL_ERROR(ErrorKind.INTERNAL, "서버 오류가 발생했습니다."),

    // 인증 · 소셜 로그인
    UNSUPPORTED_SOCIAL_PROVIDER(ErrorKind.INVALID_INPUT, "지원하지 않는 소셜 제공자입니다."),
    INVALID_SOCIAL_TOKEN(ErrorKind.UNAUTHENTICATED, "유효하지 않은 소셜 토큰입니다."),
    SOCIAL_AUTH_UNAVAILABLE(ErrorKind.UNAVAILABLE, "소셜 인증 서버와 통신할 수 없습니다. 잠시 후 다시 시도해주세요."),
    INVALID_TOKEN(ErrorKind.UNAUTHENTICATED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(ErrorKind.UNAUTHENTICATED, "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(ErrorKind.UNAUTHENTICATED, "리프레시 토큰을 찾을 수 없습니다."),
    NOT_ADMIN(ErrorKind.PERMISSION_DENIED, "백오피스 접근 권한이 없습니다."),

    // 회원
    MEMBER_NOT_FOUND(ErrorKind.NOT_FOUND, "회원을 찾을 수 없습니다."),
    ALREADY_WITHDRAWN(ErrorKind.CONFLICT, "이미 탈퇴한 회원입니다."),
    WITHDRAWN_MEMBER(ErrorKind.PERMISSION_DENIED, "탈퇴한 회원입니다."),
    INVALID_NICKNAME(ErrorKind.INVALID_INPUT, "사용할 수 없는 닉네임입니다."),

    // 대화
    CONVERSATION_NOT_FOUND(ErrorKind.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    CONVERSATION_ACCESS_DENIED(ErrorKind.PERMISSION_DENIED, "본인의 채팅방만 접근할 수 있습니다."),
    CONVERSATION_ALREADY_ENDED(ErrorKind.CONFLICT, "이미 종료된 채팅방입니다."),
    CONVERSATION_ALREADY_DELETED(ErrorKind.CONFLICT, "이미 삭제된 채팅방입니다."),
    CONVERSATION_ENDED(ErrorKind.CONFLICT, "종료된 채팅방에는 메시지를 추가할 수 없습니다."),
    CONVERSATION_NOT_ENDED(ErrorKind.CONFLICT, "종료된 채팅방에만 카드를 만들 수 있습니다."),
    INVALID_CONVERSATION_TITLE(ErrorKind.INVALID_INPUT, "사용할 수 없는 채팅방 제목입니다."),

    // 댓글 생성
    MESSAGE_NOT_FOUND(ErrorKind.NOT_FOUND, "메시지를 찾을 수 없습니다."),
    INVALID_COMMENT_TARGET(ErrorKind.INVALID_INPUT, "일기(사용자) 메시지에만 댓글을 생성할 수 있습니다."),

    // 카드
    CARD_ALREADY_EXISTS(ErrorKind.CONFLICT, "이미 카드가 생성된 채팅방입니다."),
    CARD_GENERATION_FAILED(ErrorKind.UNAVAILABLE, "카드 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
    CARD_GENERATION_IN_PROGRESS(ErrorKind.CONFLICT, "카드를 생성하는 중입니다. 잠시 후 다시 시도해주세요."),
    CARD_NOT_FOUND(ErrorKind.NOT_FOUND, "카드를 찾을 수 없습니다."),
    CARD_ACCESS_DENIED(ErrorKind.PERMISSION_DENIED, "본인의 카드만 접근할 수 있습니다."),
    CARD_ALREADY_DELETED(ErrorKind.CONFLICT, "이미 삭제된 카드입니다."),

    // 토큰 상한
    DAILY_TOKEN_LIMIT_EXCEEDED(ErrorKind.QUOTA_EXCEEDED, "오늘 사용할 수 있는 토큰을 모두 사용했습니다. 잠시 후 다시 시도해주세요."),

    // 백오피스 관리자
    ADMIN_ACCOUNT_ALREADY_EXISTS(ErrorKind.CONFLICT, "이미 등록된 관리자입니다."),
    ADMIN_ACCOUNT_NOT_FOUND(ErrorKind.NOT_FOUND, "관리자를 찾을 수 없습니다."),
    CANNOT_REMOVE_SELF(ErrorKind.CONFLICT, "자기 자신은 관리자에서 삭제할 수 없습니다."),

    // 프롬프트 리비전
    PROMPT_REVISION_NOT_FOUND(ErrorKind.NOT_FOUND, "프롬프트 리비전을 찾을 수 없습니다."),

    // 알림
    INVALID_DEVICE_TOKEN(ErrorKind.INVALID_INPUT, "사용할 수 없는 디바이스 토큰입니다."),
    ;

    val code: String get() = name
}
