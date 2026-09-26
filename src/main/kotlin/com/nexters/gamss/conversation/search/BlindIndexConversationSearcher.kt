package com.nexters.gamss.conversation.search

import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.ConversationSearchRepository
import com.nexters.gamss.global.crypto.BlindIndexer
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.util.Collections

/**
 * 블라인드 인덱스 기반 [ConversationSearcher] 구현.
 *
 * 원문이 암호문으로 저장되면서 `MATCH(content) AGAINST(...)` 가 죽었기 때문에, 저장할 때 만들어 둔
 * 토큰열([com.nexters.gamss.global.crypto.BlindIndexer])을 대신 검색한다. 검색어도 같은 토크나이저를
 * 거쳐 토큰열로 바꾼 뒤 구문 검색하므로, 토큰이 원문 순서대로 인접한 대화방만 걸린다 — ngram 파서로
 * 하던 부분 일치가 그대로 재현된다.
 *
 * **검색어에서 연산자를 제거하던 처리가 사라졌다.** 예전에는 사용자가 넣은 `+`·`*`·`"` 가 BOOLEAN MODE
 * 연산자로 해석되지 않게 걸러내야 했지만, 이제 DB로 넘어가는 것은 hex 토큰뿐이라 연산자가 섞일 수 없다.
 *
 * 매칭된 대화방 ID를 최신순으로 받은 뒤 제목·상태·일시는 JPA 엔티티에서 로드한다. 제목이 암호문이라
 * 네이티브 결과로는 읽을 수 없고, 엔티티로 로드해야 컨버터가 복호화한다.
 */
@Component
class BlindIndexConversationSearcher(
    private val conversationSearchRepository: ConversationSearchRepository,
    private val conversationRepository: ConversationRepository,
    private val indexer: BlindIndexer,
) : ConversationSearcher {
    /**
     * 제목 없는 대화방을 찾기 위한 [UNTITLED_LABEL] 의 토큰열. 검색어 토큰이 이 안에 원문 순서대로
     * 인접해 들어 있으면 제목이 없는 방도 함께 찾는다.
     *
     * **문구를 저장해 두고 맞추는 것이 아니라 검색할 때 판정한다.** 제목이 없는 방은 컬럼이 null 이라
     * 인덱스도 없고, 인덱스를 채우려면 이미 쌓인 방을 전부 다시 써야 한다. 판정은
     * [com.nexters.gamss.global.crypto.BlindIndexer.tokenize] 한 번이면 끝나므로 기존 행을 건드릴 이유가 없다.
     *
     * **판정에도 저장된 제목을 찾을 때와 같은 토크나이저를 쓴다.** 규칙을 따로 구현하면 "제목없는대화"
     * 처럼 붙여 쓴 검색어가 실제 제목에서는 안 걸리는데 이 문구에서만 걸리는 식으로 어긋난다.
     */
    private val untitledLabelTokens = indexer.tokenize(UNTITLED_LABEL)

    override fun search(
        memberId: Long,
        keyword: String,
        pageable: Pageable,
    ): Page<ConversationSearchResult> {
        val tokens = indexer.tokenize(keyword)
        if (tokens.isEmpty()) {
            return PageImpl(emptyList(), pageable, 0)
        }

        val idPage =
            conversationSearchRepository.searchConversationIds(
                memberId = memberId,
                searchTerm = toTokenPhrase(tokens),
                includeUntitled = matchesUntitledLabel(tokens),
                pageable = pageable,
            )
        val ids = idPage.content
        val conversations = conversationRepository.findAllById(ids).associateBy { it.id }

        val results =
            ids.mapNotNull { id ->
                val conversation = conversations[id] ?: return@mapNotNull null
                ConversationSearchResult(
                    conversationId = id,
                    title = conversation.title?.value,
                    status = conversation.status,
                    createdAt = conversation.createdAt,
                )
            }
        return PageImpl(results, pageable, idPage.totalElements)
    }

    /** 토큰열을 FULLTEXT 구문 검색 형태로 감싼다. 인접·순서를 요구해야 부분 일치가 재현된다. */
    private fun toTokenPhrase(tokens: List<String>): String = "\"${tokens.joinToString(" ")}\""

    /**
     * 검색어가 클라이언트의 "제목 없는 대화" 표시를 가리키는지. 구문 검색과 같은 판정이라 부분 검색어
     * ("제목")도 걸리고, 원문에서 떨어진 조합("제목 대화")은 걸리지 않는다.
     */
    private fun matchesUntitledLabel(tokens: List<String>): Boolean = Collections.indexOfSubList(untitledLabelTokens, tokens) >= 0

    companion object {
        /**
         * 제목이 null 인 방에 클라이언트가 대신 그리는 문구. 서버는 이 문구를 저장하지도 응답에 싣지도
         * 않지만(응답의 title 은 계속 null 이다), 사용자가 화면에 보이는 글자로 검색하므로 검색만은
         * 알고 있어야 한다. **클라이언트가 문구를 바꾸면 이 값도 같이 바꿔야 한다** — 안 바꾸면 검색이
         * 조용히 안 걸리기 시작한다.
         */
        private const val UNTITLED_LABEL = "제목 없는 대화"
    }
}
