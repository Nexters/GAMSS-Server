package com.nexters.gamss.conversation.search

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * 대화방을 검색한다. 지금은 MySQL 풀텍스트로 구현하지만, 규모가 커지면 같은 인터페이스 뒤에
 * Elasticsearch 구현을 끼워 넣어 호출부(서비스) 변경 없이 엔진을 교체할 수 있다.
 *
 * **엔진을 바꿀 때 평문을 색인하면 안 된다.** 본문·제목은 DB에 암호문으로만 저장되며, 검색은
 * 평문 대신 블라인드 인덱스를 맞춰 이뤄진다([com.nexters.gamss.global.crypto.BlindIndexer]).
 * 외부 검색 엔진에 원문을 그대로 넘기면 암호화가 막으려던 유출 경로가 그대로 다시 열린다.
 */
interface ConversationSearcher {
    /**
     * 회원 본인의 대화방 중 **클라이언트가 지정한 제목** 또는 채팅 내용이 [keyword]에 매칭되는 것을
     * 최신순으로 찾는다. 카드 요약(`cards.summary`)은 카드에 표시할 문구이지 대화방 제목이 아니므로
     * 검색 대상이 아니다. 다른 구현체도 이 계약을 따라야 한다.
     *
     * 삭제된 대화방은 결과에 포함하지 않는다(목록·캘린더 등 다른 조회와 같은 규약).
     * 결과의 title 은 제목을 아직 지정하지 않은 대화방이면 null 이다.
     *
     * @param keyword 정제된 검색어(공백 트림·최소 길이 검증은 호출 측 책임)
     */
    fun search(
        memberId: Long,
        keyword: String,
        pageable: Pageable,
    ): Page<ConversationSearchResult>
}
