package com.nexters.gamss.monitoring.domain

/**
 * LLM 생성 종류. 일기 댓글([COMMENT]), 유저 답글 재응답([REPLY]), 대화 종료 시 카드 한 줄([CARD]),
 * emotion 누락 시 카드 대표 감정 분류([CARD_EMOTION]),
 * 백오피스 플레이그라운드 실험([PREVIEW] - 비용 추적용으로만 기록하며 품질 지표 집계에서 제외).
 */
enum class GenerationType(
    /**
     * 일일 토큰 한도에 합산되는지. 적립과 기동 백필이 모두 이 값을 보므로 정책이 한 곳에 있다
     * ([com.nexters.gamss.tokenlimit.service.TokenQuotaRecorder],
     * [com.nexters.gamss.tokenlimit.service.TokenQuotaBackfill]) - 갈라 두면 두 기준이 어긋나
     * 재기동 시점에 따라 사용량이 달라진다.
     */
    val countsTowardQuota: Boolean,
) {
    COMMENT(countsTowardQuota = true),
    REPLY(countsTowardQuota = true),

    /**
     * 카드는 대화 1개당 1장이고 `cards.conversation_id` UNIQUE 로 묶여 반복 소비가 불가능하다 -
     * 눌러서 계속 만들 수 있는 댓글·답글과 같은 통을 쓸 이유가 없다.
     */
    CARD(countsTowardQuota = false),
    CARD_EMOTION(countsTowardQuota = false),

    /** 관리자 도구다. 비용은 나가지만 특정 회원의 한도를 깎을 이유가 없다. */
    PREVIEW(countsTowardQuota = false),
    ;

    companion object {
        /** 한도에 합산하지 않는 종류의 이름. 백필 쿼리가 제외 목록으로 쓴다. */
        fun namesNotCountingTowardQuota(): List<String> = entries.filterNot { it.countsTowardQuota }.map { it.name }
    }
}
