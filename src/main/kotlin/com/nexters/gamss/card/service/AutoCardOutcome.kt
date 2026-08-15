package com.nexters.gamss.card.service

/**
 * 자동 카드 생성 배치가 대화방 하나를 처리한 결과([DailyAutoCardScheduler]).
 *
 * [FAILED]를 뺀 나머지는 배치 입장에서 정상이다 — 다른 요청이 먼저 처리했거나, 대상이 아니게
 * 됐거나, 만들 근거가 없거나, 사용자가 직접 만들 여지를 남긴 경우다.
 */
internal enum class AutoCardOutcome(
    /** 집계 로그에 쓰는 이름. */
    val label: String,
) {
    CREATED("생성"),

    /** 다른 요청이 먼저 카드를 만들었거나 만드는 중. */
    ALREADY_HANDLED("이미 처리됨"),

    /** 대상으로 뽑힌 뒤 처리 전에 사용자가 방을 지웠다(경합). */
    SKIPPED_DELETED("삭제됨"),

    /** 탈퇴한 회원의 방 — 탈퇴 후에는 그 사람의 대화로 아무것도 새로 만들지 않는다. */
    WITHDRAWN_MEMBER("탈퇴 회원"),

    /** 카드 대사를 만들 근거가 없어 종료만 했다. 프론트가 요약을 한 번도 보내지 않은 방이다. */
    NO_SUMMARY("요약 없음"),

    TOKEN_LIMIT("토큰 상한"),
    FAILED("실패"),
}
