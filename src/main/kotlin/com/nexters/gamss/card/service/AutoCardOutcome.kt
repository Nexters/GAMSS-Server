package com.nexters.gamss.card.service

/**
 * 자동 카드 생성 배치가 대화방 하나를 처리한 결과([DailyAutoCardScheduler]).
 *
 * [ALREADY_HANDLED]·[NO_SUMMARY]·[TOKEN_LIMIT]은 배치 입장에서 정상이다 — 각각 다른 요청이 먼저
 * 처리했거나, 대사를 만들 근거가 없거나, 사용자가 직접 만들 여지를 남긴 경우다. 손을 봐야 하는 것은
 * [FAILED]뿐이다.
 */
internal enum class AutoCardOutcome(
    /** 집계 로그에 쓰는 이름. */
    val label: String,
) {
    CREATED("생성"),
    ALREADY_HANDLED("이미 처리됨"),
    NO_SUMMARY("요약 없음"),
    TOKEN_LIMIT("토큰 상한"),
    FAILED("실패"),
}
