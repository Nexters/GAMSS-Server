package com.nexters.gamss.conversation.search

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * 대화방 검색 추상화. 지금은 MySQL 풀텍스트로 구현하지만, 규모가 커지면 같은 인터페이스 뒤에
 * Elasticsearch 구현을 끼워 넣어 호출부(서비스) 변경 없이 엔진을 교체할 수 있다.
 */
interface ConversationSearchPort {
    /**
     * 회원 본인의 대화방 중 제목(카드 요약) 또는 채팅 내용이 [keyword]에 매칭되는 것을 찾는다.
     *
     * @param keyword 정제된 검색어(공백 트림·최소 길이 검증은 호출 측 책임)
     */
    fun search(
        memberId: Long,
        keyword: String,
        pageable: Pageable,
    ): Page<ConversationSearchResult>
}
