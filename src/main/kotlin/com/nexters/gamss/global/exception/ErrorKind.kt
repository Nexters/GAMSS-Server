package com.nexters.gamss.global.exception

/**
 * 에러의 성격. "무엇이 잘못됐는가"만 말하고 그것을 어떤 프로토콜로 어떻게 표현할지는 모른다.
 *
 * [ErrorCode]가 이 값을 들고 있고, HTTP 상태 코드로의 번역은 웹 계층이 한다
 * ([com.nexters.gamss.global.web.httpStatusOf]). 도메인은 닉네임 규칙을 어겼다는 사실만 알면
 * 되지 그것이 400인지 422인지 알 이유가 없고, 알게 되면 검증 규칙이 스프링 웹 타입에 묶인다.
 */
enum class ErrorKind {
    /** 입력값이 규칙에 맞지 않는다. */
    INVALID_INPUT,

    /** 누구인지 확인되지 않았다. */
    UNAUTHENTICATED,

    /** 누구인지는 알지만 그 일을 할 권한이 없다. */
    PERMISSION_DENIED,

    /** 대상이 없다. */
    NOT_FOUND,

    /** 지금 상태에서는 할 수 없는 요청이다(이미 종료·이미 삭제 등). */
    CONFLICT,

    /** 허용된 사용량을 다 썼다. */
    QUOTA_EXCEEDED,

    /** 우리 잘못이 아니라 의존하는 외부가 지금 응답하지 못한다. */
    UNAVAILABLE,

    /** 우리 잘못이다. */
    INTERNAL,
}
