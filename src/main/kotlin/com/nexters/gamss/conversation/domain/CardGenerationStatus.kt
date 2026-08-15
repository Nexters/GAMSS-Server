package com.nexters.gamss.conversation.domain

/**
 * 채팅방의 카드 생성 CAS 선점 상태. [CommentStatus]와 같은 목적 — 동시 중복 요청이 LLM을
 * 여러 번 호출하지 않도록 DB 행 락 안에서 선점·전이한다.
 *
 * NONE(초기) -> PENDING(선점, LLM 호출 중) -> DONE | FAILED
 * FAILED에서도 재선점(PENDING)이 가능하다 -
 * [com.nexters.gamss.conversation.repository.ConversationRepository.updateCardGenerationStatus] 참고.
 */
enum class CardGenerationStatus {
    NONE,
    PENDING,
    DONE,
    FAILED,

    /**
     * 자동 생성을 포기한 상태. 요약이 없어 카드 한 줄을 만들 근거가 없는데, 종료된 방에는 메시지를
     * 더 보낼 수 없어 요약이 채워질 수도 없다 — 그대로 두면 배치가 매일 밤 같은 방을 다시 집어
     * 같은 결론을 반복한다([com.nexters.gamss.card.service.DailyAutoCardScheduler]).
     *
     * 자동 생성만 포기한 것이지 카드가 불가능하다는 뜻은 아니다. 카드 생성 API는 요약을 클라이언트가
     * 실어 보내므로 사용자가 직접 만들 수 있고, 그래서 이 상태도 재선점 대상에 포함된다
     * ([com.nexters.gamss.card.service.CardService]의 claimForGeneration).
     */
    SKIPPED,
}
