package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.Message

/**
 * 댓글(일기)·답글 생성 공통 결과. "선점 상태 + 생성된 메시지들 + 토큰"이라는 개념은 두 흐름이 같아서
 * 하나의 타입으로 다룬다 — 일기는 [messages]가 0..N개, 답글은 0..1개가 된다(생성 주체가 크기를
 * 보장하고, 타입으로는 강제하지 않는다).
 */
data class GenerationResult(
    val outcome: CommentGenerationOutcome,
    val messages: List<Message> = emptyList(),
    /** 이번 요청에서 실제로 LLM을 호출해 DONE이 된 경우에만 채워진다(재조회·GENERATING·FAILED는 null). */
    val usedTokens: Int? = null,
)
