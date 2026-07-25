package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.Message

data class CommentGenerationResult(
    val outcome: CommentGenerationOutcome,
    val comments: List<Message> = emptyList(),
    /** 이번 요청에서 실제로 LLM을 호출해 DONE이 된 경우에만 채워진다(재조회·GENERATING·FAILED는 null). */
    val usedTokens: Int? = null,
)
