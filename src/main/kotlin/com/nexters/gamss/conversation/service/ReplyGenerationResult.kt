package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.Message

/** [CommentGenerationOutcome]는 댓글·답글 생성 공통 상태라 재사용한다. 답글은 항상 캐릭터 메시지 1개뿐이라 리스트가 아니다. */
data class ReplyGenerationResult(
    val outcome: CommentGenerationOutcome,
    val message: Message? = null,
    /** 이번 요청에서 실제로 LLM을 호출해 DONE이 된 경우에만 채워진다(재조회·GENERATING·FAILED는 null). */
    val usedTokens: Int? = null,
)
