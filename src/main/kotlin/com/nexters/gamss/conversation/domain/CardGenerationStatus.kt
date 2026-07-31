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
}
