package com.nexters.gamss.conversation.search

import com.nexters.gamss.conversation.domain.ConversationStatus
import java.time.Instant

/**
 * 대화방 검색 결과 한 건. 검색 엔진(MySQL·ES 등) 구현과 무관한 도메인 표현이다.
 *
 * @param title 대화방 제목(= 카드 요약). 카드가 없는(진행 중) 대화방은 null.
 */
data class ConversationSearchResult(
    val conversationId: Long,
    val title: String?,
    val status: ConversationStatus,
    val createdAt: Instant,
)
