package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.search.ConversationSearchResult
import com.nexters.gamss.conversation.search.ConversationSearcher
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 대화방 검색 서비스. 엔진에 무관한 검증(검색어 정제·최소 길이)만 담당하고, 실제 검색은
 * [ConversationSearcher] 에 위임한다. 엔진(MySQL·ES) 교체 시 이 서비스는 바뀌지 않는다.
 */
@Service
class ConversationSearchService(
    private val searcher: ConversationSearcher,
) {
    // 검색 구현이 리포지토리를 여러 번 호출하므로 한 트랜잭션·한 커넥션으로 묶는다.
    @Transactional(readOnly = true)
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
        return searcher.search(memberId, trimmed, PageRequest.of(page, size))
    }

    companion object {
        // 검색어를 bigram 으로 쪼개 맞추므로([com.nexters.gamss.global.crypto.BlindIndexer]) 1자로는
        // 토큰이 하나도 안 나온다. 옛 ngram 풀텍스트도 토큰 크기가 2였어서 이 값은 그대로다.
        private const val MIN_KEYWORD_LENGTH = 2
    }
}
