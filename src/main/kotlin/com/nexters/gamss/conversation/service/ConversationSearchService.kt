package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.search.ConversationSearchPort
import com.nexters.gamss.conversation.search.ConversationSearchResult
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

/**
 * 대화방 검색 서비스. 엔진에 무관한 검증(검색어 정제·최소 길이)만 담당하고, 실제 검색은
 * [ConversationSearchPort] 에 위임한다 — 엔진(MySQL·ES) 교체 시 이 서비스는 바뀌지 않는다.
 */
@Service
class ConversationSearchService(
    private val searchPort: ConversationSearchPort,
) {
    fun search(
        memberId: Long,
        keyword: String,
        page: Int,
        size: Int,
    ): Page<ConversationSearchResult> {
        val trimmed = keyword.trim()
        if (trimmed.length < MIN_KEYWORD_LENGTH) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "검색어는 ${MIN_KEYWORD_LENGTH}자 이상이어야 합니다.")
        }
        return searchPort.search(memberId, trimmed, PageRequest.of(page, size))
    }

    companion object {
        // ngram 파서 기본 토큰 크기(2)보다 짧으면 검색이 무의미하므로 최소 2자를 요구한다.
        private const val MIN_KEYWORD_LENGTH = 2
    }
}
