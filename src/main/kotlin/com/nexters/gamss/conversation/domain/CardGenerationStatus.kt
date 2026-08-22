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
     * 자동 생성을 포기했던 상태. 요약이 없으면 카드 한 줄을 만들 근거가 없다고 보고 배치가 못 박던
     * 값인데, 지금은 요약이 없으면 유저 메시지 원문으로 대신 만들므로(#204) **새로 저장되지 않는다.**
     *
     * 상수를 남겨 둔 것은 이미 이 값으로 저장된 행을 읽기 위해서다 — 먼저 지우면 `@Enumerated(STRING)`
     * 매핑이 그 행에서 깨진다. 그 행들은 다음 배치가 카드를 만들며 DONE으로 옮기고
     * ([com.nexters.gamss.conversation.repository.ConversationRepository.findAutoCardTargetIds]),
     * 남은 행이 없는 것을 확인한 뒤 이 상수와 재선점 목록
     * ([com.nexters.gamss.card.service.CardService]의 claimForGeneration)에서 함께 뺀다.
     */
    SKIPPED,
}
