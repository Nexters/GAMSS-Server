package com.nexters.gamss.conversation.domain

/**
 * 일기(사용자) 메시지에 대한 캐릭터 댓글 생성 상태. 캐릭터 메시지 행에서는 의미가 없다.
 *
 * NONE(초기) -> PENDING(선점, LLM 호출 중) -> DONE | FAILED
 * FAILED에서도 재선점(PENDING)이 가능하다 — [com.nexters.gamss.conversation.repository.MessageRepository.updateCommentStatus] 참고.
 */
enum class CommentStatus {
    NONE,
    PENDING,
    DONE,
    FAILED,
}
