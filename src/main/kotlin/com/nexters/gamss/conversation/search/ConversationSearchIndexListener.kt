package com.nexters.gamss.conversation.search

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.global.crypto.BlindIndexer
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import org.springframework.stereotype.Component

/**
 * 대화방을 저장하기 직전에 제목의 검색용 블라인드 인덱스를 계산해 채운다.
 *
 * 인덱스를 **어떻게** 만드는지는 여기까지만 안다. [Conversation]은 완성된 토큰열을 건네받아 보관만 한다.
 *
 * 제목은 [Conversation.rename] 으로 여러 번 바뀌므로 `@PreUpdate` 도 함께 건다. 제목이 아닌 값만
 * 바뀐 갱신에서도 다시 계산되지만, 제목은 100자 이하라 비용이 무시할 수준이고 "언제 다시 계산해야
 * 하는가"를 따로 추적하는 쪽이 어긋나기 쉽다.
 *
 * 제목을 바꾸는 벌크 UPDATE 는 없다(있다면 리스너가 돌지 않아 인덱스만 옛 제목으로 남는다).
 * [com.nexters.gamss.conversation.repository.ConversationRepository] 의 벌크 쿼리는 상태·시각 컬럼만
 * 건드린다. 새로 추가할 때 확인할 것.
 */
@Component
class ConversationSearchIndexListener(
    private val indexer: BlindIndexer,
) {
    @PrePersist
    @PreUpdate
    fun fillSearchIndex(conversation: Conversation) {
        conversation.applySearchIndex(conversation.title?.value?.let(indexer::toIndexValue))
    }
}
