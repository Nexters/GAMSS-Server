package com.nexters.gamss.conversation.search

import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.ConversationSearchRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

/**
 * MySQL 8.0 풀텍스트(ngram 파서) 기반 대화방 검색 [ConversationSearchPort] 구현.
 *
 * 검색어를 BOOLEAN MODE 구문("...")으로 감싸 부분 일치처럼 동작시킨다. 매칭된 대화방 ID를 최신순으로
 * 받은 뒤, 제목(카드 요약)·상태·일시는 JPA 엔티티에서 로드해 붙인다(네이티브 타입 매핑 이슈 회피).
 */
@Component
class MysqlConversationSearch(
    private val conversationSearchRepository: ConversationSearchRepository,
    private val conversationRepository: ConversationRepository,
    private val cardRepository: CardRepository,
) : ConversationSearchPort {
    override fun search(
        memberId: Long,
        keyword: String,
        pageable: Pageable,
    ): Page<ConversationSearchResult> {
        val phrase = toBooleanPhrase(keyword)
        if (phrase.isBlank()) {
            return PageImpl(emptyList(), pageable, 0)
        }

        val idPage = conversationSearchRepository.searchConversationIds(memberId, phrase, pageable)
        val ids = idPage.content
        val conversations = conversationRepository.findAllById(ids).associateBy { it.id }
        val titlesByConversationId = cardRepository.findByConversationIdIn(ids).associate { it.conversationId to it.summary }

        val results =
            ids.mapNotNull { id ->
                val conversation = conversations[id] ?: return@mapNotNull null
                ConversationSearchResult(
                    conversationId = id,
                    title = titlesByConversationId[id],
                    status = conversation.status,
                    createdAt = conversation.createdAt,
                )
            }
        return PageImpl(results, pageable, idPage.totalElements)
    }

    /**
     * ngram BOOLEAN MODE 구문 검색어로 변환한다. 특수 연산자를 제거하고 큰따옴표로 감싸,
     * bigram 이 연속으로 나타나는(=부분 일치) 대화방만 매칭되게 한다.
     */
    private fun toBooleanPhrase(keyword: String): String {
        val sanitized = keyword.replace(BOOLEAN_OPERATORS, " ").trim()
        if (sanitized.isBlank()) {
            return ""
        }
        return "\"$sanitized\""
    }

    companion object {
        private val BOOLEAN_OPERATORS = Regex("[\"+\\-><()~*@]")
    }
}
