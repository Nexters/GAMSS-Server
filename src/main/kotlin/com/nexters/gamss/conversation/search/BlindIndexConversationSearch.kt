package com.nexters.gamss.conversation.search

import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.ConversationSearchRepository
import com.nexters.gamss.global.crypto.BlindIndexer
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

/**
 * 블라인드 인덱스 기반 [ConversationSearchPort] 구현.
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
class BlindIndexConversationSearch(
    private val conversationSearchRepository: ConversationSearchRepository,
    private val conversationRepository: ConversationRepository,
    private val indexer: BlindIndexer,
) : ConversationSearchPort {
    override fun search(
        memberId: Long,
        keyword: String,
        pageable: Pageable,
    ): Page<ConversationSearchResult> {
        val phrase = toTokenPhrase(keyword) ?: return PageImpl(emptyList(), pageable, 0)

        val idPage = conversationSearchRepository.searchConversationIds(memberId, phrase, pageable)
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

    /**
     * 검색어를 토큰열 구문으로 바꾼다. 토큰이 하나도 안 나오면(모든 어절이 1글자) null 을 돌려
     * 호출부가 빈 결과로 끝내게 한다 — 어떤 행과도 매칭될 수 없는 검색어라 질의할 이유가 없다.
     */
    private fun toTokenPhrase(keyword: String): String? {
        val tokens = indexer.tokenize(keyword)
        return if (tokens.isEmpty()) null else "\"${tokens.joinToString(" ")}\""
    }
}
